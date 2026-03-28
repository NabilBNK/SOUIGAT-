from django.db.models import Count, Sum, Value
from django.db.models.functions import Coalesce

from api.models import CargoTicket, PassengerTicket, TripExpense, TripReportSnapshot


TERMINAL_CARGO_STATUSES = ['cancelled', 'refunded', 'lost', 'refused']


def _passenger_agg(trip):
    return PassengerTicket.objects.filter(
        trip=trip,
        status='active',
    ).aggregate(
        total=Coalesce(Sum('price'), Value(0)),
        count=Count('id'),
    )


def _cargo_agg(trip):
    return CargoTicket.objects.filter(
        trip=trip,
    ).exclude(
        status__in=TERMINAL_CARGO_STATUSES,
    ).aggregate(
        total=Coalesce(Sum('price'), Value(0)),
        count=Count('id'),
    )


def _expense_total(trip):
    return TripExpense.objects.filter(trip=trip).aggregate(
        total=Coalesce(Sum('amount'), Value(0)),
    )['total']


def upsert_trip_report_snapshots_for_trip(trip):
    """Persist report snapshots when trip is completed and settlement is settled."""
    settlement = getattr(trip, 'settlement', None)
    if settlement is None:
        TripReportSnapshot.objects.filter(trip=trip).delete()
        return

    if trip.status != 'completed' or settlement.status != 'settled':
        TripReportSnapshot.objects.filter(trip=trip).delete()
        return

    passenger = _passenger_agg(trip)
    cargo = _cargo_agg(trip)
    expense_total = int(_expense_total(trip) or 0)
    passenger_revenue = int(passenger['total'] or 0)
    cargo_revenue = int(cargo['total'] or 0)
    net_revenue = passenger_revenue + cargo_revenue - expense_total
    report_date = trip.departure_datetime.date()

    for office in (trip.origin_office, trip.destination_office):
        TripReportSnapshot.objects.update_or_create(
            trip=trip,
            office=office,
            defaults={
                'report_date': report_date,
                'total_trips': 1,
                'total_passengers': int(passenger['count'] or 0),
                'total_cargo': int(cargo['count'] or 0),
                'passenger_revenue': passenger_revenue,
                'cargo_revenue': cargo_revenue,
                'expense_total': expense_total,
                'net_revenue': net_revenue,
                'settlement_status': settlement.status,
                'source_updated_at': settlement.updated_at,
            },
        )
