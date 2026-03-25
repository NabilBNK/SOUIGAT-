import hashlib
import logging
from datetime import datetime, timedelta, timezone as dt_timezone
from random import randint
from threading import Thread
from typing import Any, Dict, Tuple

from django.db import transaction
from django.utils import timezone

from api.models import CargoTicket, FirebaseMirrorEvent, PassengerTicket, PricingConfig, Settlement, Trip, TripExpense
from api.services.firebase_admin import FirebaseConfigurationError, get_firebase_app

try:
    from firebase_admin import firestore
except ModuleNotFoundError:  # pragma: no cover
    firestore = None

logger = logging.getLogger(__name__)

ENTITY_TRIP = 'trip'
ENTITY_PASSENGER_TICKET = 'passenger_ticket'
ENTITY_CARGO_TICKET = 'cargo_ticket'
ENTITY_TRIP_EXPENSE = 'trip_expense'
ENTITY_SETTLEMENT = 'settlement'
ENTITY_PRICING_CONFIG = 'pricing_config'

ENTITY_COLLECTIONS = {
    ENTITY_TRIP: 'trip_mirror_v1',
    ENTITY_PASSENGER_TICKET: 'passenger_ticket_mirror_v1',
    ENTITY_CARGO_TICKET: 'cargo_ticket_mirror_v1',
    ENTITY_TRIP_EXPENSE: 'trip_expense_mirror_v1',
    ENTITY_SETTLEMENT: 'settlement_mirror_v1',
    ENTITY_PRICING_CONFIG: 'pricing_config_mirror_v1',
}


class StaleMirrorConflictError(RuntimeError):
    """Raised when incoming source_updated_at is older than mirrored document."""


class NonRetryableMirrorError(RuntimeError):
    """Raised when a mirror event should fail terminally."""


def _to_iso(dt: datetime | None) -> str | None:
    if dt is None:
        return None
    if timezone.is_naive(dt):
        dt = timezone.make_aware(dt, timezone=dt_timezone.utc)
    return dt.astimezone(dt_timezone.utc).isoformat()


def _coerce_datetime(value: Any) -> datetime:
    if isinstance(value, datetime):
        dt = value
    elif isinstance(value, str):
        parsed = datetime.fromisoformat(value.replace('Z', '+00:00'))
        dt = parsed
    else:
        raise ValueError(f'Unsupported datetime value: {value!r}')

    if timezone.is_naive(dt):
        dt = timezone.make_aware(dt, timezone=dt_timezone.utc)
    return dt


def _to_epoch_ms(iso_value: str | None) -> int | None:
    if not iso_value:
        return None
    try:
        parsed = _coerce_datetime(iso_value)
    except Exception:
        return None
    return int(parsed.timestamp() * 1000)


def _trip_scope(trip: Trip) -> list[int]:
    return [trip.origin_office_id, trip.destination_office_id]


def _pricing_scope(pricing: PricingConfig) -> list[int]:
    return [pricing.origin_office_id, pricing.destination_office_id]


def resolve_entity_type(instance: Any) -> str:
    if isinstance(instance, Trip):
        return ENTITY_TRIP
    if isinstance(instance, PassengerTicket):
        return ENTITY_PASSENGER_TICKET
    if isinstance(instance, CargoTicket):
        return ENTITY_CARGO_TICKET
    if isinstance(instance, TripExpense):
        return ENTITY_TRIP_EXPENSE
    if isinstance(instance, Settlement):
        return ENTITY_SETTLEMENT
    if isinstance(instance, PricingConfig):
        return ENTITY_PRICING_CONFIG
    raise ValueError(f'Unsupported entity type for Firebase mirror: {instance.__class__.__name__}')


def _source_updated_at(instance: Any) -> datetime:
    if hasattr(instance, 'updated_at') and instance.updated_at:
        return _coerce_datetime(instance.updated_at)
    if hasattr(instance, 'created_at') and instance.created_at:
        return _coerce_datetime(instance.created_at)
    return timezone.now()


def _build_trip_payload(trip: Trip) -> Dict[str, Any]:
    source_created_at = _to_iso(trip.created_at)
    source_updated_at = _to_iso(trip.updated_at)
    return {
        'entity': ENTITY_TRIP,
        'id': trip.id,
        'status': trip.status,
        'origin_office_id': trip.origin_office_id,
        'origin_office_name': trip.origin_office.name if trip.origin_office_id and trip.origin_office else '',
        'destination_office_id': trip.destination_office_id,
        'destination_office_name': trip.destination_office.name if trip.destination_office_id and trip.destination_office else '',
        'office_scope_ids': _trip_scope(trip),
        'conductor_id': trip.conductor_id,
        'conductor_name': trip.conductor.get_full_name() if trip.conductor_id and trip.conductor else '',
        'bus_id': trip.bus_id,
        'bus_plate': trip.bus.plate_number if trip.bus_id and trip.bus else '',
        'departure_datetime': _to_iso(trip.departure_datetime),
        'departure_ts': _to_epoch_ms(_to_iso(trip.departure_datetime)),
        'arrival_datetime': _to_iso(trip.arrival_datetime),
        'passenger_base_price': trip.passenger_base_price,
        'cargo_small_price': trip.cargo_small_price,
        'cargo_medium_price': trip.cargo_medium_price,
        'cargo_large_price': trip.cargo_large_price,
        'currency': trip.currency,
        'source_created_at': source_created_at,
        'source_updated_at': source_updated_at,
        'is_deleted': bool(getattr(trip, 'is_deleted', False)),
        'deleted_at': _to_iso(getattr(trip, 'deleted_at', None)),
        'sync_version': 1,
    }


def _build_passenger_ticket_payload(ticket: PassengerTicket) -> Dict[str, Any]:
    trip = ticket.trip
    source_created_at = _to_iso(ticket.created_at)
    source_updated_at = _to_iso(ticket.updated_at)
    return {
        'entity': ENTITY_PASSENGER_TICKET,
        'id': ticket.id,
        'trip_id': ticket.trip_id,
        'ticket_number': ticket.ticket_number,
        'passenger_name': ticket.passenger_name,
        'seat_number': ticket.seat_number,
        'price': ticket.price,
        'currency': ticket.currency,
        'payment_source': ticket.payment_source,
        'status': ticket.status,
        'created_by_id': ticket.created_by_id,
        'created_by_name': ticket.created_by.get_full_name() if ticket.created_by_id and ticket.created_by else '',
        'conductor_id': trip.conductor_id if trip else None,
        'office_scope_ids': _trip_scope(trip) if trip else [],
        'source_created_at': source_created_at,
        'source_updated_at': source_updated_at,
        'is_deleted': bool(getattr(ticket, 'is_deleted', False)),
        'deleted_at': _to_iso(getattr(ticket, 'deleted_at', None)),
        'sync_version': 1,
    }


def _build_cargo_ticket_payload(ticket: CargoTicket) -> Dict[str, Any]:
    trip = ticket.trip
    source_created_at = _to_iso(ticket.created_at)
    source_updated_at = _to_iso(ticket.updated_at)
    return {
        'entity': ENTITY_CARGO_TICKET,
        'id': ticket.id,
        'trip_id': ticket.trip_id,
        'ticket_number': ticket.ticket_number,
        'sender_name': ticket.sender_name,
        'sender_phone': ticket.sender_phone,
        'receiver_name': ticket.receiver_name,
        'receiver_phone': ticket.receiver_phone,
        'cargo_tier': ticket.cargo_tier,
        'description': ticket.description,
        'price': ticket.price,
        'currency': ticket.currency,
        'payment_source': ticket.payment_source,
        'status': ticket.status,
        'status_override_reason': ticket.status_override_reason,
        'status_override_by_id': ticket.status_override_by_id,
        'delivered_at': _to_iso(ticket.delivered_at),
        'delivered_by_id': ticket.delivered_by_id,
        'created_by_id': ticket.created_by_id,
        'created_by_name': ticket.created_by.get_full_name() if ticket.created_by_id and ticket.created_by else '',
        'conductor_id': trip.conductor_id if trip else None,
        'office_scope_ids': _trip_scope(trip) if trip else [],
        'source_created_at': source_created_at,
        'source_updated_at': source_updated_at,
        'is_deleted': bool(getattr(ticket, 'is_deleted', False)),
        'deleted_at': _to_iso(getattr(ticket, 'deleted_at', None)),
        'sync_version': 1,
    }


def _build_trip_expense_payload(expense: TripExpense) -> Dict[str, Any]:
    trip = expense.trip
    source_created_at = _to_iso(expense.created_at)
    source_updated_at = _to_iso(expense.updated_at)
    return {
        'entity': ENTITY_TRIP_EXPENSE,
        'id': expense.id,
        'trip_id': expense.trip_id,
        'description': expense.description,
        'category': expense.category,
        'amount': expense.amount,
        'currency': expense.currency,
        'created_by_id': expense.created_by_id,
        'created_by_name': expense.created_by.get_full_name() if expense.created_by_id and expense.created_by else '',
        'conductor_id': trip.conductor_id if trip else None,
        'office_scope_ids': _trip_scope(trip) if trip else [],
        'source_created_at': source_created_at,
        'source_updated_at': source_updated_at,
        'is_deleted': False,
        'deleted_at': None,
        'sync_version': 1,
    }


def _build_settlement_payload(settlement: Settlement) -> Dict[str, Any]:
    trip = settlement.trip
    source_created_at = _to_iso(settlement.created_at)
    source_updated_at = _to_iso(settlement.updated_at)
    return {
        'entity': ENTITY_SETTLEMENT,
        'id': settlement.id,
        'trip_id': settlement.trip_id,
        'trip_status': trip.status if trip else None,
        'office_id': settlement.office_id,
        'office_name': settlement.office.name if settlement.office_id and settlement.office else '',
        'conductor_id': settlement.conductor_id,
        'conductor_name': settlement.conductor.get_full_name() if settlement.conductor_id and settlement.conductor else '',
        'settled_by_id': settlement.settled_by_id,
        'expected_passenger_cash': settlement.expected_passenger_cash,
        'expected_cargo_cash': settlement.expected_cargo_cash,
        'expected_total_cash': settlement.expected_total_cash,
        'agency_presale_total': settlement.agency_presale_total,
        'outstanding_cargo_delivery': settlement.outstanding_cargo_delivery,
        'expenses_to_reimburse': settlement.expenses_to_reimburse,
        'net_cash_expected': settlement.net_cash_expected,
        'actual_cash_received': settlement.actual_cash_received,
        'actual_expenses_reimbursed': settlement.actual_expenses_reimbursed,
        'discrepancy_amount': settlement.discrepancy_amount,
        'status': settlement.status,
        'notes': settlement.notes,
        'dispute_reason': settlement.dispute_reason,
        'settled_at': _to_iso(settlement.settled_at),
        'disputed_at': _to_iso(settlement.disputed_at),
        'resolved_at': _to_iso(settlement.resolved_at),
        'conductor_scope_id': trip.conductor_id if trip else None,
        'office_scope_ids': _trip_scope(trip) if trip else [settlement.office_id],
        'source_created_at': source_created_at,
        'source_updated_at': source_updated_at,
        'is_deleted': False,
        'deleted_at': None,
        'sync_version': 1,
    }


def _build_pricing_config_payload(pricing: PricingConfig) -> Dict[str, Any]:
    source_created_at = _to_iso(pricing.created_at)
    source_updated_at = _to_iso(pricing.updated_at)
    return {
        'entity': ENTITY_PRICING_CONFIG,
        'id': pricing.id,
        'origin_office_id': pricing.origin_office_id,
        'origin_office_name': pricing.origin_office.name if pricing.origin_office_id and pricing.origin_office else '',
        'destination_office_id': pricing.destination_office_id,
        'destination_office_name': pricing.destination_office.name if pricing.destination_office_id and pricing.destination_office else '',
        'office_scope_ids': _pricing_scope(pricing),
        'route_key': f'{pricing.origin_office_id}:{pricing.destination_office_id}',
        'passenger_price': pricing.passenger_price,
        'cargo_small_price': pricing.cargo_small_price,
        'cargo_medium_price': pricing.cargo_medium_price,
        'cargo_large_price': pricing.cargo_large_price,
        'currency': pricing.currency,
        'effective_from': pricing.effective_from.isoformat() if pricing.effective_from else None,
        'effective_until': pricing.effective_until.isoformat() if pricing.effective_until else None,
        'is_active': pricing.is_active,
        'source_created_at': source_created_at,
        'source_updated_at': source_updated_at,
        'is_deleted': bool(getattr(pricing, 'is_deleted', False)),
        'deleted_at': _to_iso(getattr(pricing, 'deleted_at', None)),
        'sync_version': 1,
    }


def build_payload_from_instance(entity_type: str, instance: Any) -> Dict[str, Any]:
    if entity_type == ENTITY_TRIP:
        return _build_trip_payload(instance)
    if entity_type == ENTITY_PASSENGER_TICKET:
        return _build_passenger_ticket_payload(instance)
    if entity_type == ENTITY_CARGO_TICKET:
        return _build_cargo_ticket_payload(instance)
    if entity_type == ENTITY_TRIP_EXPENSE:
        return _build_trip_expense_payload(instance)
    if entity_type == ENTITY_SETTLEMENT:
        return _build_settlement_payload(instance)
    if entity_type == ENTITY_PRICING_CONFIG:
        return _build_pricing_config_payload(instance)
    raise ValueError(f'Unsupported mirror entity: {entity_type}')


def build_delete_payload(entity_type: str, entity_id: str | int, source_updated_at: datetime) -> Dict[str, Any]:
    source_updated_iso = _to_iso(source_updated_at)
    return {
        'entity': entity_type,
        'id': int(entity_id),
        'source_updated_at': source_updated_iso,
        'is_deleted': True,
        'deleted_at': _to_iso(timezone.now()),
        'sync_version': 1,
    }


def build_op_id(entity_type: str, entity_id: str, operation: str, source_updated_at: datetime) -> str:
    source_updated_iso = _to_iso(source_updated_at) or ''
    raw = f'{entity_type}:{entity_id}:{operation}:{source_updated_iso}'
    digest = hashlib.sha256(raw.encode('utf-8')).hexdigest()[:24]
    return f'{entity_type}:{entity_id}:{operation}:{digest}'


def enqueue_mirror_event(
    entity_type: str,
    entity_id: str,
    operation: str,
    source_updated_at: datetime,
    payload: Dict[str, Any],
    max_attempts: int = 8,
) -> FirebaseMirrorEvent:
    source_updated_dt = _coerce_datetime(source_updated_at)
    op_id = build_op_id(entity_type, entity_id, operation, source_updated_dt)
    now = timezone.now()

    event, _created = FirebaseMirrorEvent.objects.update_or_create(
        op_id=op_id,
        defaults={
            'entity_type': entity_type,
            'entity_id': entity_id,
            'operation': operation,
            'payload': payload,
            'source_updated_at': source_updated_dt,
            'status': FirebaseMirrorEvent.STATUS_PENDING,
            'attempts': 0,
            'max_attempts': max_attempts,
            'next_retry_at': now,
            'last_error': '',
            'synced_at': None,
        },
    )
    return event


def enqueue_instance_upsert(instance: Any) -> FirebaseMirrorEvent:
    entity_type = resolve_entity_type(instance)
    payload = build_payload_from_instance(entity_type, instance)
    source_updated_at = _source_updated_at(instance)
    return enqueue_mirror_event(
        entity_type=entity_type,
        entity_id=str(instance.pk),
        operation=FirebaseMirrorEvent.OPERATION_UPSERT,
        source_updated_at=source_updated_at,
        payload=payload,
    )


def enqueue_instance_delete(instance: Any) -> FirebaseMirrorEvent:
    entity_type = resolve_entity_type(instance)
    source_updated_at = _source_updated_at(instance)
    payload = build_delete_payload(entity_type, instance.pk, source_updated_at)
    return enqueue_mirror_event(
        entity_type=entity_type,
        entity_id=str(instance.pk),
        operation=FirebaseMirrorEvent.OPERATION_DELETE,
        source_updated_at=source_updated_at,
        payload=payload,
    )


def schedule_mirror_event(event_id: int) -> None:
    from api.tasks import process_firebase_mirror_event

    def _dispatch():
        def _run():
            try:
                process_firebase_mirror_event.delay(event_id)
                return
            except Exception:
                logger.exception(
                    'Failed to dispatch Firebase mirror event %s to Celery. Falling back to background inline processing.',
                    event_id,
                )

            try:
                process_firebase_mirror_event(event_id)
            except Exception:
                logger.exception(
                    'Background inline processing failed for Firebase mirror event %s.',
                    event_id,
                )

        Thread(target=_run, daemon=True, name=f'firebase-mirror-{event_id}').start()

    transaction.on_commit(_dispatch)


def _firestore_client():
    if firestore is None:
        raise NonRetryableMirrorError('firebase-admin firestore client is not installed.')
    app = get_firebase_app()
    return firestore.client(app=app)


def _parse_remote_source_updated(value: Any) -> datetime | None:
    if value is None:
        return None
    try:
        return _coerce_datetime(value)
    except Exception:
        return None


def _collection_for(entity_type: str) -> str:
    collection = ENTITY_COLLECTIONS.get(entity_type)
    if not collection:
        raise NonRetryableMirrorError(f'No Firestore collection configured for entity type: {entity_type}')
    return collection


def apply_mirror_event(event: FirebaseMirrorEvent) -> Tuple[bool, str]:
    client = _firestore_client()
    collection_name = _collection_for(event.entity_type)
    doc_ref = client.collection(collection_name).document(str(event.entity_id))
    incoming_updated_at = _coerce_datetime(event.source_updated_at)

    @firestore.transactional
    def _apply(transaction_handle):
        snapshot = doc_ref.get(transaction=transaction_handle)
        existing_data = snapshot.to_dict() if snapshot.exists else None

        if existing_data:
            existing_op_id = existing_data.get('last_op_id')
            if isinstance(existing_op_id, str) and existing_op_id == event.op_id:
                return False, 'duplicate-op'

            existing_updated_at = _parse_remote_source_updated(existing_data.get('source_updated_at'))
            if existing_updated_at and existing_updated_at > incoming_updated_at:
                raise StaleMirrorConflictError(
                    f'Incoming event {event.op_id} is older than mirrored document state.',
                )

        payload = dict(event.payload or {})
        payload['last_op_id'] = event.op_id
        payload['mirrored_at'] = firestore.SERVER_TIMESTAMP
        payload['source_updated_at'] = _to_iso(incoming_updated_at)

        if event.operation == FirebaseMirrorEvent.OPERATION_DELETE:
            payload['is_deleted'] = True
            payload.setdefault('deleted_at', _to_iso(timezone.now()))

        transaction_handle.set(doc_ref, payload, merge=True)
        return True, 'synced'

    firestore_tx = client.transaction()
    return _apply(firestore_tx)


def compute_retry_delay(attempts: int) -> timedelta:
    base = min(120, 2 ** max(0, attempts - 1))
    jitter = randint(0, 7)
    return timedelta(seconds=base + jitter)
