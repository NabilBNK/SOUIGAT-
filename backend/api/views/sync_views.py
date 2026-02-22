import logging
from decimal import Decimal, InvalidOperation

from django.db import transaction
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework.exceptions import ValidationError

from api.models import (
    CargoTicket, PassengerTicket, SyncLog, QuarantinedSync, Trip, TripExpense,
)
from api.serializers.sync import SyncBatchSerializer

logger = logging.getLogger(__name__)

VALID_CARGO_TIERS = {'small', 'medium', 'large'}


@api_view(['POST'])
def batch_sync(request):
    """
    Batch sync endpoint for mobile conductors.

    Processes offline-created tickets and expenses with:
    - Content-hash idempotency (duplicate skip)
    - Per-item savepoints (partial failure resilience)
    - Resume support via resume_from index
    """
    if not request.user.is_authenticated or request.user.role != 'conductor':
        return Response(
            {'detail': 'Only conductors can sync.'},
            status=status.HTTP_403_FORBIDDEN,
        )

    serializer = SyncBatchSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    trip_id = serializer.validated_data['trip_id']
    items = serializer.validated_data['items']
    resume_from = serializer.validated_data['resume_from']

    results = []
    accepted = 0
    quarantined = 0
    duplicates = 0
    # Bug #7 fix: track last processed index (accepted + duplicate), not just accepted
    last_processed_index = resume_from - 1

    with transaction.atomic():
        try:
            trip = Trip.objects.select_for_update().get(pk=trip_id)
        except Trip.DoesNotExist:
            raise ValidationError({'trip_id': 'Trip does not exist.'})

        if trip.conductor_id != request.user.id:
            raise ValidationError(
                {'trip_id': 'You are not the assigned conductor for this trip.'}
            )

        if trip.status not in ('scheduled', 'in_progress'):
            raise ValidationError(
                {'trip_id': f'Trip is not open for sync. Status: {trip.status}'}
            )

        for idx, item in enumerate(items):
            if idx < resume_from:
                continue

            key = item['idempotency_key']

            # Idempotency check
            if SyncLog.objects.filter(key=key).exists():
                results.append({
                    'index': idx, 'status': 'duplicate', 'key': key,
                })
                duplicates += 1
                last_processed_index = idx  # Bug #7 fix
                continue

            # Per-item savepoint
            sid = transaction.savepoint()
            try:
                record_id = _process_item(
                    item_type=item['type'],
                    payload=item['payload'],
                    trip=trip,
                    user=request.user,
                )
                transaction.savepoint_commit(sid)

                results.append({
                    'index': idx, 'status': 'accepted', 'id': record_id,
                })
                accepted += 1
                last_processed_index = idx  # Bug #7 fix

            except Exception as e:
                transaction.savepoint_rollback(sid)
                reason = str(e)

                # Bug #2 fix: protect QuarantinedSync.create with its own savepoint
                quarantine_sid = transaction.savepoint()
                try:
                    QuarantinedSync.objects.create(
                        conductor=request.user,
                        trip=trip,
                        original_data={
                            'type': item['type'],
                            'payload': item['payload'],
                            'idempotency_key': key,
                        },
                        reason=reason,
                    )
                    transaction.savepoint_commit(quarantine_sid)
                except Exception:
                    transaction.savepoint_rollback(quarantine_sid)
                    logger.error(
                        'Failed to quarantine item %d for trip %d: %s',
                        idx, trip.id, reason, exc_info=True,
                    )

                results.append({
                    'index': idx, 'status': 'quarantined', 'reason': reason,
                })
                quarantined += 1

            # Record idempotency key regardless of outcome
            SyncLog.objects.get_or_create(
                key=key,
                defaults={
                    'conductor': request.user,
                    'trip': trip,
                    'accepted': 1 if results[-1]['status'] == 'accepted' else 0,
                    'quarantined': 1 if results[-1]['status'] == 'quarantined' else 0,
                },
            )

    return Response({
        'sync_log_trip': trip_id,
        'accepted': accepted,
        'quarantined': quarantined,
        'duplicates': duplicates,
        'last_processed_index': last_processed_index,
        'items': results,
    }, status=status.HTTP_200_OK)


def _process_item(item_type, payload, trip, user):
    """
    Route sync item to appropriate create logic.

    Returns the created record's ID.
    Raises Exception on validation failure (caught by caller).
    """
    if item_type == 'passenger_ticket':
        return _create_passenger_ticket(payload, trip, user)
    elif item_type == 'cargo_ticket':
        return _create_cargo_ticket(payload, trip, user)
    elif item_type == 'expense':
        return _create_expense(payload, trip, user)
    else:
        raise ValidationError(f'Unknown item type: {item_type}')


def _create_passenger_ticket(payload, trip, user):
    """Create passenger ticket from sync payload."""
    # Bug #1 fix: use total count (all statuses) for numbering, active for capacity
    active_count = PassengerTicket.objects.filter(
        trip=trip, status='active',
    ).count()
    if active_count >= trip.bus.capacity:
        raise ValidationError('Bus is at full capacity.')

    total_count = PassengerTicket.objects.filter(trip=trip).count()
    ticket_number = f'PT-{trip.id}-{total_count + 1:03d}'

    ticket = PassengerTicket.objects.create(
        trip=trip,
        ticket_number=ticket_number,
        passenger_name=payload.get('passenger_name', 'Walk-in'),
        price=trip.passenger_base_price,
        currency=trip.currency,
        payment_source=payload.get('payment_source', 'cash'),
        seat_number=payload.get('seat_number', ''),
        created_by=user,
    )
    return ticket.id


def _create_cargo_ticket(payload, trip, user):
    """Create cargo ticket from sync payload."""
    tier = payload.get('cargo_tier', 'small')

    # Bug #3 fix: validate tier instead of silent fallback to 0
    if tier not in VALID_CARGO_TIERS:
        raise ValidationError(
            f"Invalid cargo_tier '{tier}'. Must be one of: {', '.join(sorted(VALID_CARGO_TIERS))}."
        )

    price_map = {
        'small': trip.cargo_small_price,
        'medium': trip.cargo_medium_price,
        'large': trip.cargo_large_price,
    }
    price = price_map[tier]  # KeyError impossible after validation

    count = CargoTicket.objects.filter(trip=trip).count()
    ticket_number = f'CT-{trip.id}-{count + 1:03d}'

    cargo = CargoTicket.objects.create(
        trip=trip,
        ticket_number=ticket_number,
        sender_name=payload.get('sender_name', ''),
        sender_phone=payload.get('sender_phone', ''),
        receiver_name=payload.get('receiver_name', ''),
        receiver_phone=payload.get('receiver_phone', ''),
        cargo_tier=tier,
        description=payload.get('description', ''),
        price=price,
        currency=trip.currency,
        payment_source=payload.get('payment_source', 'prepaid'),
        created_by=user,
    )
    return cargo.id


def _create_expense(payload, trip, user):
    """Create trip expense from sync payload."""
    description = payload.get('description', '')

    # Bug #4 fix: use Decimal for safe numeric handling
    raw = payload.get('amount')
    try:
        amount = Decimal(str(raw))
    except (TypeError, ValueError, InvalidOperation):
        raise ValidationError(f'Invalid expense amount: {raw!r}')

    if amount <= 0:
        raise ValidationError('Expense amount must be positive.')

    expense = TripExpense.objects.create(
        trip=trip,
        description=description,
        amount=amount,
        currency=trip.currency,
        created_by=user,
    )
    return expense.id
