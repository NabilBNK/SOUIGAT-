import logging
from dataclasses import asdict, dataclass
from datetime import datetime, timezone as dt_timezone

from django.db import IntegrityError
from django.db.models import Count, Sum, Value
from django.db.models.functions import Coalesce
from django.utils import timezone

from api.models import CargoTicket, PassengerTicket, Settlement, TripExpense
from api.services.firebase_admin import get_firebase_app

try:
    from firebase_admin import firestore
except ModuleNotFoundError:  # pragma: no cover - optional at runtime
    firestore = None


logger = logging.getLogger(__name__)

_CARGO_TERMINAL_STATUSES = {'cancelled', 'refunded', 'lost', 'refused'}
_CARGO_POD_RESOLVED_STATUSES = {'paid', 'cancelled', 'refunded', 'lost', 'refused'}


@dataclass(frozen=True)
class SettlementComputation:
    expected_passenger_cash: int
    expected_cargo_cash: int
    expected_total_cash: int
    agency_presale_total: int
    outstanding_cargo_delivery: int
    expenses_to_reimburse: int
    net_cash_expected: int
    active_passenger_cash_count: int
    active_passenger_presale_count: int
    prepaid_cargo_count: int
    pod_cargo_count: int
    expense_count: int

    @property
    def snapshot(self):
        return asdict(self)


def _aggregate_sum_count(queryset, sum_field):
    return queryset.aggregate(
        total=Coalesce(Sum(sum_field), Value(0)),
        count=Count('id'),
    )


def _safe_int(value):
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, (int, float)):
        return int(value)
    if isinstance(value, str):
        try:
            return int(float(value))
        except ValueError:
            return 0
    return 0


def _safe_text(value):
    if value is None:
        return ''
    return str(value).strip()


def _parse_iso_datetime(value):
    if value is None:
        return None

    parsed = None
    if isinstance(value, datetime):
        parsed = value
    elif isinstance(value, str):
        raw = value.strip()
        if not raw:
            return None
        if raw.endswith('Z'):
            raw = raw[:-1] + '+00:00'
        try:
            parsed = datetime.fromisoformat(raw)
        except ValueError:
            return None

    if parsed is None:
        return None

    if timezone.is_naive(parsed):
        return timezone.make_aware(parsed, dt_timezone.utc)
    return parsed.astimezone(dt_timezone.utc)


def _firestore_client():
    if firestore is None:
        return None

    try:
        app = get_firebase_app()
        return firestore.client(app=app)
    except Exception:  # pragma: no cover - runtime env dependent
        logger.debug('Firebase Firestore client unavailable for settlement mirror reads.', exc_info=True)
        return None


def _get_trip_mirror_payload(client, trip_id):
    trip_id = int(trip_id)

    try:
        snapshot = client.collection('trip_mirror_v1').document(str(trip_id)).get()
        if snapshot.exists:
            return snapshot.to_dict() or {}
    except Exception:  # pragma: no cover - runtime env dependent
        logger.warning('Failed to read trip mirror document by id=%s', trip_id, exc_info=True)

    try:
        snapshots = client.collection('trip_mirror_v1').where('id', '==', trip_id).limit(1).stream()
        for snapshot in snapshots:
            return snapshot.to_dict() or {}
    except Exception:  # pragma: no cover - runtime env dependent
        logger.warning('Failed to query trip mirror by field id=%s', trip_id, exc_info=True)

    return None


def get_trip_mirror_completion(trip_id):
    client = _firestore_client()
    if client is None:
        return False, None

    payload = _get_trip_mirror_payload(client, trip_id)
    if not payload or payload.get('is_deleted') is True:
        return False, None

    status = _safe_text(payload.get('status'))
    if status != 'completed':
        return False, None

    arrival_datetime = _parse_iso_datetime(payload.get('arrival_datetime'))
    return True, arrival_datetime


def _fetch_trip_mirror_docs(client, collection_name, trip_id):
    trip_id = int(trip_id)
    try:
        snapshots = client.collection(collection_name).where('trip_id', '==', trip_id).stream()
        return [snapshot.to_dict() or {} for snapshot in snapshots]
    except Exception:  # pragma: no cover - runtime env dependent
        logger.warning(
            'Failed to read %s mirror docs for trip_id=%s',
            collection_name,
            trip_id,
            exc_info=True,
        )
        return []


def _compute_settlement_from_mirror(trip_id):
    client = _firestore_client()
    if client is None:
        return None

    passenger_docs = _fetch_trip_mirror_docs(client, 'passenger_ticket_mirror_v1', trip_id)
    cargo_docs = _fetch_trip_mirror_docs(client, 'cargo_ticket_mirror_v1', trip_id)
    expense_docs = _fetch_trip_mirror_docs(client, 'trip_expense_mirror_v1', trip_id)

    if not passenger_docs and not cargo_docs and not expense_docs:
        return None

    passenger_cash_total = 0
    passenger_cash_count = 0
    passenger_presale_total = 0
    passenger_presale_count = 0
    cargo_prepaid_total = 0
    cargo_prepaid_count = 0
    cargo_pod_outstanding_total = 0
    cargo_pod_count = 0
    expenses_total = 0
    expense_count = 0

    for ticket in passenger_docs:
        if ticket.get('is_deleted') is True:
            continue
        if _safe_text(ticket.get('status')) != 'active':
            continue

        price = _safe_int(ticket.get('price'))
        payment_source = _safe_text(ticket.get('payment_source'))

        if payment_source == 'cash':
            passenger_cash_total += price
            passenger_cash_count += 1
        elif payment_source == 'prepaid':
            passenger_presale_total += price
            passenger_presale_count += 1

    for cargo_ticket in cargo_docs:
        if cargo_ticket.get('is_deleted') is True:
            continue

        status = _safe_text(cargo_ticket.get('status'))
        payment_source = _safe_text(cargo_ticket.get('payment_source'))
        price = _safe_int(cargo_ticket.get('price'))

        if payment_source == 'prepaid':
            if status in _CARGO_TERMINAL_STATUSES:
                continue
            cargo_prepaid_total += price
            cargo_prepaid_count += 1
            continue

        if payment_source == 'pay_on_delivery':
            if status in _CARGO_POD_RESOLVED_STATUSES:
                continue
            cargo_pod_outstanding_total += price
            cargo_pod_count += 1

    for expense in expense_docs:
        if expense.get('is_deleted') is True:
            continue
        expenses_total += _safe_int(expense.get('amount'))
        expense_count += 1

    expected_total_cash = passenger_cash_total + cargo_prepaid_total
    net_cash_expected = expected_total_cash - expenses_total

    return SettlementComputation(
        expected_passenger_cash=passenger_cash_total,
        expected_cargo_cash=cargo_prepaid_total,
        expected_total_cash=expected_total_cash,
        agency_presale_total=passenger_presale_total,
        outstanding_cargo_delivery=cargo_pod_outstanding_total,
        expenses_to_reimburse=expenses_total,
        net_cash_expected=net_cash_expected,
        active_passenger_cash_count=passenger_cash_count,
        active_passenger_presale_count=passenger_presale_count,
        prepaid_cargo_count=cargo_prepaid_count,
        pod_cargo_count=cargo_pod_count,
        expense_count=expense_count,
    )


def compute_settlement(trip):
    mirror_computation = _compute_settlement_from_mirror(trip.id)
    if mirror_computation is not None:
        return mirror_computation

    passenger_cash = _aggregate_sum_count(
        PassengerTicket.objects.filter(
            trip=trip, status='active', payment_source='cash',
        ),
        'price',
    )
    agency_presale = _aggregate_sum_count(
        PassengerTicket.objects.filter(
            trip=trip, status='active', payment_source='prepaid',
        ),
        'price',
    )
    cargo_cash = _aggregate_sum_count(
        CargoTicket.objects.filter(
            trip=trip, payment_source='prepaid',
        ).exclude(status__in=['cancelled', 'refunded', 'lost', 'refused']),
        'price',
    )
    pod_outstanding = _aggregate_sum_count(
        CargoTicket.objects.filter(
            trip=trip, payment_source='pay_on_delivery',
        ).exclude(status__in=['paid', 'cancelled', 'refunded', 'lost', 'refused']),
        'price',
    )
    expenses = _aggregate_sum_count(
        TripExpense.objects.filter(trip=trip),
        'amount',
    )

    expected_total_cash = int(passenger_cash['total']) + int(cargo_cash['total'])
    expenses_to_reimburse = int(expenses['total'])

    return SettlementComputation(
        expected_passenger_cash=int(passenger_cash['total']),
        expected_cargo_cash=int(cargo_cash['total']),
        expected_total_cash=expected_total_cash,
        agency_presale_total=int(agency_presale['total']),
        outstanding_cargo_delivery=int(pod_outstanding['total']),
        expenses_to_reimburse=expenses_to_reimburse,
        net_cash_expected=expected_total_cash - expenses_to_reimburse,
        active_passenger_cash_count=int(passenger_cash['count']),
        active_passenger_presale_count=int(agency_presale['count']),
        prepaid_cargo_count=int(cargo_cash['count']),
        pod_cargo_count=int(pod_outstanding['count']),
        expense_count=int(expenses['count']),
    )


def initiate_settlement_for_trip(trip):
    computation = compute_settlement(trip)
    defaults = {
        'office': trip.destination_office,
        'conductor': trip.conductor,
        'expected_passenger_cash': computation.expected_passenger_cash,
        'expected_cargo_cash': computation.expected_cargo_cash,
        'expected_total_cash': computation.expected_total_cash,
        'agency_presale_total': computation.agency_presale_total,
        'outstanding_cargo_delivery': computation.outstanding_cargo_delivery,
        'expenses_to_reimburse': computation.expenses_to_reimburse,
        'net_cash_expected': computation.net_cash_expected,
        'calculation_snapshot': computation.snapshot,
    }
    try:
        settlement, created = Settlement.objects.get_or_create(
            trip=trip, defaults=defaults,
        )
    except IntegrityError:
        settlement = Settlement.objects.get(trip=trip)
        created = False
    return settlement, created


def recompute_settlement_for_trip(trip):
    """Recompute expected settlement totals for an existing trip settlement."""
    settlement, _created = initiate_settlement_for_trip(trip)
    computation = compute_settlement(trip)

    settlement.expected_passenger_cash = computation.expected_passenger_cash
    settlement.expected_cargo_cash = computation.expected_cargo_cash
    settlement.expected_total_cash = computation.expected_total_cash
    settlement.agency_presale_total = computation.agency_presale_total
    settlement.outstanding_cargo_delivery = computation.outstanding_cargo_delivery
    settlement.expenses_to_reimburse = computation.expenses_to_reimburse
    settlement.net_cash_expected = computation.net_cash_expected
    settlement.calculation_snapshot = computation.snapshot
    settlement.save(update_fields=[
        'expected_passenger_cash',
        'expected_cargo_cash',
        'expected_total_cash',
        'agency_presale_total',
        'outstanding_cargo_delivery',
        'expenses_to_reimburse',
        'net_cash_expected',
        'calculation_snapshot',
        'updated_at',
    ])
    return settlement


def build_settlement_preview(settlement):
    return {
        'settlement_id': settlement.id,
        'status': settlement.status,
        'office_name': settlement.office.name,
        'expected_total_cash': settlement.expected_total_cash,
        'expenses_to_reimburse': settlement.expenses_to_reimburse,
        'net_cash_expected': settlement.net_cash_expected,
        'agency_presale_total': settlement.agency_presale_total,
        'outstanding_cargo_delivery': settlement.outstanding_cargo_delivery,
    }


def serialize_settlement_audit(settlement):
    return {
        'id': settlement.id,
        'trip_id': settlement.trip_id,
        'office_id': settlement.office_id,
        'conductor_id': settlement.conductor_id,
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
        'calculation_snapshot': settlement.calculation_snapshot,
        'settled_at': settlement.settled_at.isoformat() if settlement.settled_at else None,
        'disputed_at': settlement.disputed_at.isoformat() if settlement.disputed_at else None,
        'resolved_at': settlement.resolved_at.isoformat() if settlement.resolved_at else None,
        'created_at': settlement.created_at.isoformat() if settlement.created_at else None,
        'updated_at': settlement.updated_at.isoformat() if settlement.updated_at else None,
    }
