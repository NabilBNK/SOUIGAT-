import logging

from django.db import transaction
from django.utils import timezone
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework.exceptions import ValidationError

from api.models import (
    CargoTicket, PassengerTicket, SyncLog, QuarantinedSync, Trip, TripExpense,
)
from api.serializers.sync import SyncBatchSerializer, SyncLogResultSerializer
from api.signals import suppress_report_signals, _invalidate_report_cache
from api.permissions import get_cached_user_permissions
from api.services.money_scale import (
    looks_like_legacy_mobile_expense_amount,
    looks_like_legacy_mobile_passenger_price,
    normalize_money_amount,
    parse_integer_money,
)

logger = logging.getLogger(__name__)

VALID_CARGO_TIERS = {'small', 'medium', 'large'}


def _quarantine_item(*, idx, item, key, trip, user, reason):
    with transaction.atomic():
        QuarantinedSync.objects.create(
            conductor=user,
            trip=trip,
            original_data={
                'type': item['type'],
                'payload': item['payload'],
                'idempotency_key': key,
            },
            reason=reason,
        )


@api_view(['POST'])
def batch_sync(request):
    """
    Batch sync endpoint for mobile conductors.

    Processes offline-created tickets and expenses with:
    - Content-hash idempotency (duplicate skip)
    - Per-item savepoints (partial failure resilience)
    - Resume support via resume_from index
    """
    if not request.user.is_authenticated:
        return Response(
            {'detail': 'Authentication required.'},
            status=status.HTTP_403_FORBIDDEN,
        )

    if 'sync_batch' not in get_cached_user_permissions(request):
        return Response(
            {'detail': 'Only authorized conductors can sync.'},
            status=status.HTTP_403_FORBIDDEN,
        )

    serializer = SyncBatchSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    trip_id = serializer.validated_data['trip_id']
    items = serializer.validated_data['items']
    resume_from = serializer.validated_data['resume_from']
    # resume_from is already validated by SyncBatchSerializer: 0 <= resume_from < len(items)

    results = []
    accepted = 0
    quarantined = 0
    duplicates = 0
    # Bug #7 fix: track last processed index (accepted + duplicate), not just accepted
    last_processed_index = resume_from - 1

    # Suppress per-ticket signal-based cache invalidation during batch sync.
    # Context manager guarantees flag is cleared even on exception (try/finally).
    # Cache is invalidated once after the atomic block completes.
    with suppress_report_signals():
        with transaction.atomic():
            try:
                trip = Trip.objects.select_for_update().select_related(
                    'bus', 'origin_office', 'destination_office'
                ).get(pk=trip_id)
            except Trip.DoesNotExist:
                raise ValidationError({'trip_id': 'Trip does not exist.'})

            if trip.conductor_id != request.user.id:
                raise ValidationError(
                    {'trip_id': 'You are not the assigned conductor for this trip.'}
                )

            # Bug #1 fix: quarantine ALL items instead of rejecting when trip is closed
            trip_closed = trip.status not in ('scheduled', 'in_progress')

            # Batch idempotency check
            keys_to_check = [item['idempotency_key'] for item in items[resume_from:]]
            existing_keys = set(SyncLog.objects.filter(key__in=keys_to_check).values_list('key', flat=True))

            # Cache counts for processing
            trip_state = {
                'total_passenger_count': PassengerTicket.objects.filter(trip=trip).count(),
                'cargo_count': CargoTicket.objects.filter(trip=trip).count(),
            }

            for idx, item in enumerate(items):
                if idx < resume_from:
                    continue

                key = item['idempotency_key']

                # Idempotency check
                if key in existing_keys:
                    results.append({
                        'index': idx, 'status': 'duplicate', 'key': key,
                    })
                    duplicates += 1
                    last_processed_index = idx
                    continue

                # If trip is closed, quarantine instead of processing
                if trip_closed:
                    try:
                        _quarantine_item(
                            idx=idx,
                            item=item,
                            key=key,
                            trip=trip,
                            user=request.user,
                            reason=f'Trip status is {trip.status} (not open for sync)',
                        )
                    except Exception:
                        logger.error(
                            'Failed to quarantine item %d for closed trip %d',
                            idx, trip.id, exc_info=True,
                        )

                    SyncLog.objects.get_or_create(
                        key=key,
                        defaults={
                            'conductor': request.user, 'trip': trip,
                            'accepted': 0, 'quarantined': 1,
                        },
                    )
                    existing_keys.add(key)
                    results.append({
                        'index': idx, 'status': 'quarantined',
                        'reason': f'Trip {trip.status}',
                    })
                    quarantined += 1
                    last_processed_index = idx
                    continue

                try:
                    with transaction.atomic():
                        record_id = _process_item(
                            item_type=item['type'],
                            payload=item['payload'],
                            trip=trip,
                            user=request.user,
                            trip_state=trip_state,
                        )
                except Exception as e:
                    reason = str(e)

                    try:
                        _quarantine_item(
                            idx=idx,
                            item=item,
                            key=key,
                            trip=trip,
                            user=request.user,
                            reason=reason,
                        )
                    except Exception:
                        logger.error(
                            'Failed to quarantine item %d for trip %d: %s',
                            idx, trip.id, reason, exc_info=True,
                        )

                    local_id = item.get('local_id')
                    results.append({
                        'index': idx, 'status': 'quarantined', 'reason': reason,
                        **(({'local_id': local_id}) if local_id is not None else {}),
                    })
                    quarantined += 1
                else:
                    local_id = item.get('local_id')
                    results.append({
                        'index': idx, 'status': 'accepted', 'id': record_id,
                        **(({'local_id': local_id}) if local_id is not None else {}),
                    })
                    accepted += 1
                    last_processed_index = idx

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
                existing_keys.add(key)
                last_processed_index = idx

    # Invalidate report cache once for the entire batch (outside both context managers)
    if accepted > 0 or quarantined > 0:
        _invalidate_report_cache(trip)

    return Response({
        'sync_log_trip': trip_id,
        'accepted': accepted,
        'quarantined': quarantined,
        'duplicates': duplicates,
        'last_processed_index': last_processed_index,
        'items': results,
    }, status=status.HTTP_200_OK)


@api_view(['GET'])
def sync_log_result(request, key):
    """
    Retrieve the original accepted/quarantined result for an idempotency key.

    Used for admin debugging — retrieve the original per-item sync result.
    The mobile SyncWorker does NOT call this endpoint; per-item 'duplicate'
    status in the batch response provides sufficient information.

    JWT-scoped: conductors can only see their own sync log entries.
    Returns 404 for unknown keys or keys belonging to other conductors.
    """
    if not request.user.is_authenticated:
        return Response(
            {'detail': 'Authentication required.'},
            status=status.HTTP_403_FORBIDDEN,
        )

    if 'sync_batch' not in get_cached_user_permissions(request):
        return Response(
            {'detail': 'Not authorized.'},
            status=status.HTTP_403_FORBIDDEN,
        )

    try:
        sync_log = SyncLog.objects.get(key=key, conductor=request.user)
    except SyncLog.DoesNotExist:
        return Response(
            {'detail': 'Sync log entry not found.'},
            status=status.HTTP_404_NOT_FOUND,
        )

    serializer = SyncLogResultSerializer(sync_log)
    return Response(serializer.data, status=status.HTTP_200_OK)


def _process_item(item_type, payload, trip, user, trip_state):
    """
    Route sync item to appropriate create logic.

    Returns the created record's ID.
    Raises Exception on validation failure (caught by caller).
    """
    if item_type == 'passenger_ticket':
        return _create_passenger_ticket(payload, trip, user, trip_state)
    elif item_type == 'cargo_ticket':
        return _create_cargo_ticket(payload, trip, user, trip_state)
    elif item_type == 'expense':
        return _create_expense(payload, trip, user)
    else:
        raise ValidationError(f'Unknown item type: {item_type}')


def _count_occupied_seats_for_boarding(trip, boarding_point):
    """
    Capacity is evaluated at the boarding stop:
    active passengers alighting at this same stop free their seat first.
    """
    active_qs = PassengerTicket.objects.filter(trip=trip, status='active')
    return active_qs.count() - active_qs.filter(
        alighting_point__iexact=boarding_point,
    ).count()


def _create_passenger_ticket(payload, trip, user, trip_state):
    """Create passenger ticket from sync payload."""
    passenger_name = str(payload.get('passenger_name', 'Walk-in'))[:100]
    payment_source = payload.get('payment_source', 'cash')
    if payment_source not in ('cash', 'prepaid'):
        raise ValidationError(f"Invalid payment_source: '{payment_source}'")

    boarding_point = str(payload.get('boarding_point') or trip.origin_office.name).strip()
    alighting_point = str(payload.get('alighting_point') or trip.destination_office.name).strip()
    if not boarding_point:
        raise ValidationError('boarding_point is required.')
    if not alighting_point:
        raise ValidationError('alighting_point is required.')
    if boarding_point == alighting_point:
        raise ValidationError('alighting_point must differ from boarding_point.')

    occupied_count = _count_occupied_seats_for_boarding(trip, boarding_point)
    if occupied_count >= trip.bus.capacity:
        raise ValidationError(
            f'Bus is at full capacity for boarding at {boarding_point} ({trip.bus.capacity} seats).'
        )

    raw_price = payload.get('price')
    if raw_price in (None, ''):
        price = trip.passenger_base_price
    else:
        money_scale = payload.get('money_scale')
        try:
            price = parse_integer_money(raw_price, field_name='price')
            price = normalize_money_amount(price, money_scale=money_scale)
        except ValueError as exc:
            raise ValidationError(str(exc))

        if money_scale is None and looks_like_legacy_mobile_passenger_price(price, trip.passenger_base_price):
            logger.warning(
                'Normalizing legacy mobile passenger price for trip %d: raw=%s normalized=%s',
                trip.id,
                raw_price,
                price // 100,
            )
            price = price // 100

        if price < 100:
            raise ValidationError('Ticket price must be at least 100 DA.')

    total_count = trip_state['total_passenger_count']
    ticket_number = f'PT-{trip.id}-{total_count + 1:03d}'

    ticket = PassengerTicket.objects.create(
        trip=trip,
        ticket_number=ticket_number,
        passenger_name=passenger_name,
        price=price,
        currency=trip.currency,
        payment_source=payment_source,
        boarding_point=boarding_point,
        alighting_point=alighting_point,
        seat_number=str(payload.get('seat_number', ''))[:10],
        synced_at=timezone.now(),
        created_by=user,
    )

    trip_state['total_passenger_count'] += 1

    return ticket.id


def _create_cargo_ticket(payload, trip, user, trip_state):
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

    count = trip_state['cargo_count']
    ticket_number = f'CT-{trip.id}-{count + 1:03d}'
    trip_state['cargo_count'] += 1

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
        amount = parse_integer_money(raw, field_name='expense amount')
        amount = normalize_money_amount(amount, money_scale=payload.get('money_scale'))
    except ValueError as exc:
        raise ValidationError(str(exc))

    if payload.get('money_scale') is None and looks_like_legacy_mobile_expense_amount(amount, description):
        logger.warning(
            'Normalizing legacy mobile expense amount for trip %d: raw=%s normalized=%s',
            trip.id,
            raw,
            amount // 100,
        )
        amount = amount // 100

    if amount <= 0:
        raise ValidationError('Expense amount must be positive.')

    expense = TripExpense.objects.create(
        trip=trip,
        description=description,
        amount=amount,
        currency=trip.currency,
        synced_at=timezone.now(),
        created_by=user,
    )
    return expense.id
