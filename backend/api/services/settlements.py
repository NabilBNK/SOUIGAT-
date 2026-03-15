from dataclasses import asdict, dataclass

from django.db import IntegrityError
from django.db.models import Count, Sum, Value
from django.db.models.functions import Coalesce

from api.models import CargoTicket, PassengerTicket, Settlement, TripExpense


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


def compute_settlement(trip):
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
