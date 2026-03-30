from collections import defaultdict
from datetime import date, timedelta

from django.core.cache import cache
from django.db import models
from django.db.models import Count, Q, Sum, Value
from django.db.models.functions import Coalesce
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework.exceptions import PermissionDenied, ValidationError

from api.models import (
    CargoTicket,
    Office,
    PassengerTicket,
    Trip,
    TripExpense,
    TripReportSnapshot,
    User,
)
from api.permissions import get_cached_user_permissions


REPORT_ROLES = ('admin',)
MAX_REPORT_RANGE_DAYS = 31
GLOBAL_REPORT_OFFICE_ID = 0
GLOBAL_REPORT_OFFICE_LABEL = 'Toutes les agences'

def _check_report_perm(request, required_perm='view_office_reports'):
    """Reports are admin-only and still require the report matrix permission."""
    if not request.user.is_authenticated:
        raise PermissionDenied('Authentication required.')

    if request.user.role != 'admin':
        raise PermissionDenied('Reports are admin-only.')

    perms = get_cached_user_permissions(request)
    if required_perm not in perms:
        raise PermissionDenied('Insufficient permissions for reports.')

def _parse_date_range(request):
    """Extract date_from / date_to from query params. Default: today."""
    today = date.today()
    date_from_raw = request.query_params.get('date_from')
    date_to_raw = request.query_params.get('date_to')

    try:
        date_from = date.fromisoformat(date_from_raw) if date_from_raw else today
        date_to = date.fromisoformat(date_to_raw) if date_to_raw else today
    except ValueError:
        raise ValidationError({
            'error_code': 'INVALID_DATE_RANGE',
            'detail': 'date_from and date_to must use YYYY-MM-DD format.',
        })

    if date_to < date_from:
        raise ValidationError({
            'error_code': 'INVALID_DATE_RANGE',
            'detail': 'date_to must be greater than or equal to date_from.',
        })

    if (date_to - date_from).days > (MAX_REPORT_RANGE_DAYS - 1):
        raise ValidationError({
            'error_code': 'DATE_RANGE_TOO_LARGE',
            'detail': f'Report date range cannot exceed {MAX_REPORT_RANGE_DAYS} days.',
        })

    return date_from, date_to


def _scope_trips(user, qs):
    """Filter trips by user scope."""
    if user.role == 'admin':
        return qs
    return qs.filter(
        Q(origin_office_id=user.office_id)
        | Q(destination_office_id=user.office_id)
    )


def _reportable_trips_for_day(day):
    """Trips that are eligible for financial reporting on a given departure date."""
    return Trip.objects.filter(
        departure_datetime__date=day,
        status='completed',
        settlement__status='settled',
    )


def _build_office_day_row(office, day, trip_ids):
    """Build a single report row for one office on one date."""
    if not trip_ids:
        return {
            'date': str(day),
            'office_name': office.name,
            'office_id': office.id,
            'total_trips': 0,
            'total_passengers': 0,
            'total_cargo': 0,
            'passenger_revenue': 0,
            'cargo_revenue': 0,
            'expense_total': 0,
            'net_revenue': 0,
        }

    p_agg = PassengerTicket.objects.filter(
        trip_id__in=trip_ids, status='active',
    ).aggregate(
        rev=Coalesce(Sum('price'), Value(0)),
        cnt=Count('id'),
    )
    c_agg = CargoTicket.objects.filter(
        trip_id__in=trip_ids,
    ).exclude(status='cancelled').aggregate(
        rev=Coalesce(Sum('price'), Value(0)),
        cnt=Count('id'),
    )
    e_agg = TripExpense.objects.filter(
        trip_id__in=trip_ids,
    ).aggregate(
        total=Coalesce(Sum('amount'), Value(0)),
    )

    p_rev = p_agg['rev']
    c_rev = c_agg['rev']
    exp = e_agg['total']

    return {
        'date': str(day),
        'office_name': office.name,
        'office_id': office.id,
        'total_trips': len(trip_ids),
        'total_passengers': p_agg['cnt'],
        'total_cargo': c_agg['cnt'],
        'passenger_revenue': p_rev,
        'cargo_revenue': c_rev,
        'expense_total': exp,
        'net_revenue': p_rev + c_rev - exp,
    }


def _build_global_day_row(day, trip_ids):
    """Build one global report row for all offices combined for a given day."""
    if not trip_ids:
        return {
            'date': str(day),
            'office_name': GLOBAL_REPORT_OFFICE_LABEL,
            'office_id': GLOBAL_REPORT_OFFICE_ID,
            'total_trips': 0,
            'total_passengers': 0,
            'total_cargo': 0,
            'passenger_revenue': 0,
            'cargo_revenue': 0,
            'expense_total': 0,
            'net_revenue': 0,
        }

    p_agg = PassengerTicket.objects.filter(
        trip_id__in=trip_ids, status='active',
    ).aggregate(
        rev=Coalesce(Sum('price'), Value(0)),
        cnt=Count('id'),
    )
    c_agg = CargoTicket.objects.filter(
        trip_id__in=trip_ids,
    ).exclude(status='cancelled').aggregate(
        rev=Coalesce(Sum('price'), Value(0)),
        cnt=Count('id'),
    )
    e_agg = TripExpense.objects.filter(
        trip_id__in=trip_ids,
    ).aggregate(
        total=Coalesce(Sum('amount'), Value(0)),
    )

    p_rev = p_agg['rev']
    c_rev = c_agg['rev']
    exp = e_agg['total']

    return {
        'date': str(day),
        'office_name': GLOBAL_REPORT_OFFICE_LABEL,
        'office_id': GLOBAL_REPORT_OFFICE_ID,
        'total_trips': len(trip_ids),
        'total_passengers': p_agg['cnt'],
        'total_cargo': c_agg['cnt'],
        'passenger_revenue': p_rev,
        'cargo_revenue': c_rev,
        'expense_total': exp,
        'net_revenue': p_rev + c_rev - exp,
    }


def _merge_daily_rows(office_name, office_id, day, live_row, snapshot_rows):
    merged = {
        'date': str(day),
        'office_name': office_name,
        'office_id': office_id,
        'total_trips': int(live_row['total_trips']),
        'total_passengers': int(live_row['total_passengers']),
        'total_cargo': int(live_row['total_cargo']),
        'passenger_revenue': int(live_row['passenger_revenue']),
        'cargo_revenue': int(live_row['cargo_revenue']),
        'expense_total': int(live_row['expense_total']),
        'net_revenue': int(live_row['net_revenue']),
    }

    for snapshot in snapshot_rows:
        merged['total_trips'] += int(snapshot.total_trips)
        merged['total_passengers'] += int(snapshot.total_passengers)
        merged['total_cargo'] += int(snapshot.total_cargo)
        merged['passenger_revenue'] += int(snapshot.passenger_revenue)
        merged['cargo_revenue'] += int(snapshot.cargo_revenue)
        merged['expense_total'] += int(snapshot.expense_total)
        merged['net_revenue'] += int(snapshot.net_revenue)

    return merged


@api_view(['GET'])
def daily_report(request):
    """
    Daily revenue summary — returns array of per-office per-date rows.

    ?date_from=YYYY-MM-DD&date_to=YYYY-MM-DD (default: today)
    ?office_id=N (admin only — filter to specific office)
    """
    _check_report_perm(request)
    date_from, date_to = _parse_date_range(request)

    office_id_param = request.query_params.get('office_id')
    office_scope = str(office_id_param) if office_id_param else 'all'
    global_scope = not office_id_param

    cache_key = f'report:daily:{office_scope}:{date_from}:{date_to}'
    cached = cache.get(cache_key)
    if cached:
        return Response(cached)

    rows = []
    if global_scope:
        snapshot_rows = TripReportSnapshot.objects.filter(
            report_date__gte=date_from,
            report_date__lte=date_to,
            settlement_status='settled',
        )
        snapshot_by_day = defaultdict(dict)
        for snapshot in snapshot_rows:
            snapshot_by_day[snapshot.report_date].setdefault(snapshot.trip_id, snapshot)

        current = date_from
        while current <= date_to:
            trip_ids = list(
                _reportable_trips_for_day(current).values_list('id', flat=True)
            )

            scoped_snapshots = snapshot_by_day.get(current, {})
            matching_snapshots = [
                scoped_snapshots[trip_id]
                for trip_id in trip_ids
                if trip_id in scoped_snapshots
            ]
            snapshot_trip_ids = {snapshot.trip_id for snapshot in matching_snapshots}
            live_trip_ids = [trip_id for trip_id in trip_ids if trip_id not in snapshot_trip_ids]

            live_row = _build_global_day_row(current, live_trip_ids)
            rows.append(_merge_daily_rows(
                GLOBAL_REPORT_OFFICE_LABEL,
                GLOBAL_REPORT_OFFICE_ID,
                current,
                live_row,
                matching_snapshots,
            ))
            current += timedelta(days=1)
    else:
        offices = list(Office.objects.filter(id=office_id_param, is_active=True))
        office_ids = [office.id for office in offices]

        snapshot_rows = TripReportSnapshot.objects.filter(
            office_id__in=office_ids,
            report_date__gte=date_from,
            report_date__lte=date_to,
            settlement_status='settled',
        )
        snapshot_by_scope = defaultdict(dict)
        for snapshot in snapshot_rows:
            scope_key = (snapshot.office_id, snapshot.report_date)
            snapshot_by_scope[scope_key][snapshot.trip_id] = snapshot

        current = date_from
        while current <= date_to:
            for office in offices:
                trips = _reportable_trips_for_day(current).filter(
                    Q(origin_office=office) | Q(destination_office=office)
                )
                trip_ids = list(trips.values_list('id', flat=True))

                scoped_snapshots = snapshot_by_scope.get((office.id, current), {})
                matching_snapshots = [
                    scoped_snapshots[trip_id]
                    for trip_id in trip_ids
                    if trip_id in scoped_snapshots
                ]
                snapshot_trip_ids = {snapshot.trip_id for snapshot in matching_snapshots}
                live_trip_ids = [trip_id for trip_id in trip_ids if trip_id not in snapshot_trip_ids]

                live_row = _build_office_day_row(office, current, live_trip_ids)
                rows.append(_merge_daily_rows(
                    office.name,
                    office.id,
                    current,
                    live_row,
                    matching_snapshots,
                ))
            current += timedelta(days=1)

    cache.set(cache_key, rows, timeout=900)  # 15-min cache
    return Response(rows)

@api_view(['GET'])
def trip_report(request, trip_id):
    """
    Detailed single-trip breakdown.

    Returns ticket list, cargo by tier, expenses, and totals.
    """
    _check_report_perm(request)

    trips = _scope_trips(request.user, Trip.objects.all())
    try:
        trip = trips.select_related(
            'origin_office', 'destination_office', 'conductor', 'bus',
        ).get(pk=trip_id)
    except Trip.DoesNotExist:
        return Response(
            {'detail': 'Trip not found.'}, status=status.HTTP_404_NOT_FOUND,
        )

    passenger_rev = PassengerTicket.objects.filter(
        trip=trip, status='active',
    ).aggregate(total=Coalesce(Sum('price'), Value(0)))['total']

    cargo_by_tier = dict(
        CargoTicket.objects.filter(trip=trip)
        .exclude(status='cancelled')
        .values('cargo_tier')
        .annotate(
            count=Count('id'),
            revenue=Coalesce(Sum('price'), Value(0)),
        )
        .values_list('cargo_tier', 'revenue')
    )
    cargo_rev = sum(cargo_by_tier.values())

    expenses = list(
        TripExpense.objects.filter(trip=trip)
        .values('description', 'amount', 'currency', 'category')
    )
    expense_total = sum(e['amount'] for e in expenses)

    data = {
        'trip_id': trip.id,
        'origin': str(trip.origin_office),
        'destination': str(trip.destination_office),
        'departure': trip.departure_datetime,
        'status': trip.status,
        'conductor': str(trip.conductor) if trip.conductor else None,
        'passenger_revenue': passenger_rev,
        'cargo_revenue': cargo_rev,
        'expense_total': expense_total,
        'net': passenger_rev + cargo_rev - expense_total,
        'ticket_count': PassengerTicket.objects.filter(
            trip=trip, status='active',
        ).count(),
        'cargo_by_tier': cargo_by_tier,
        'expenses': expenses,
    }

    return Response(data)


@api_view(['GET'])
def route_report(request):
    """
    Route aggregation summary.

    ?date_from=YYYY-MM-DD&date_to=YYYY-MM-DD
    Groups by origin→destination pair.
    Admin only.
    """
    _check_report_perm(request)

    if request.user.role != 'admin':
        raise PermissionDenied('Route reports are admin-only.')

    date_from, date_to = _parse_date_range(request)

    trips = Trip.objects.filter(
        departure_datetime__date__gte=date_from,
        departure_datetime__date__lte=date_to,
        status='completed',
        settlement__status='settled',
    )
    trips = _scope_trips(request.user, trips)

    # Group trips by route
    route_groups = (
        trips.values('origin_office__name', 'destination_office__name', 'origin_office_id', 'destination_office_id')
        .annotate(trip_count=Count('id', distinct=True))
        .order_by('-trip_count')
    )

    data = []
    for route in route_groups:
        # Separate subqueries per route to avoid Cartesian inflation
        route_trip_ids = list(
            trips.filter(
                origin_office_id=route['origin_office_id'],
                destination_office_id=route['destination_office_id'],
            ).values_list('id', flat=True)
        )

        p_rev = PassengerTicket.objects.filter(
            trip_id__in=route_trip_ids, status='active',
        ).aggregate(
            rev=Coalesce(Sum('price'), Value(0)),
        )['rev']

        c_rev = CargoTicket.objects.filter(
            trip_id__in=route_trip_ids,
        ).exclude(status='cancelled').aggregate(
            rev=Coalesce(Sum('price'), Value(0)),
        )['rev']

        data.append({
            'origin': route['origin_office__name'],
            'destination': route['destination_office__name'],
            'trip_count': route['trip_count'],
            'total_revenue': p_rev + c_rev,
            'passenger_revenue': p_rev,
            'cargo_revenue': c_rev,
        })

    return Response(data)


@api_view(['GET'])
def conductor_report(request):
    """
    Conductor performance summary — admin only.

    ?date_from=YYYY-MM-DD&date_to=YYYY-MM-DD
    Returns array of conductor stats.
    """
    _check_report_perm(request)

    if request.user.role != 'admin':
        raise PermissionDenied('Conductor reports are admin-only.')

    date_from, date_to = _parse_date_range(request)

    # Get all conductors who have trips in this range
    trips_in_range = Trip.objects.filter(
        departure_datetime__date__gte=date_from,
        departure_datetime__date__lte=date_to,
        status='completed',
        settlement__status='settled',
    )
    conductor_ids = list(
        trips_in_range.values_list('conductor_id', flat=True).distinct()
    )

    conductors = User.objects.filter(id__in=conductor_ids)

    data = []
    for conductor in conductors:
        c_trips = trips_in_range.filter(conductor=conductor)
        trip_ids = list(c_trips.values_list('id', flat=True))
        trip_count = len(trip_ids)

        if not trip_ids:
            continue

        p_agg = PassengerTicket.objects.filter(
            trip_id__in=trip_ids, status='active',
        ).aggregate(
            rev=Coalesce(Sum('price'), Value(0)),
            cnt=Count('id'),
        )
        c_agg = CargoTicket.objects.filter(
            trip_id__in=trip_ids,
        ).exclude(status='cancelled').aggregate(
            rev=Coalesce(Sum('price'), Value(0)),
            cnt=Count('id'),
        )
        e_agg = TripExpense.objects.filter(
            trip_id__in=trip_ids,
        ).aggregate(
            total=Coalesce(Sum('amount'), Value(0)),
        )

        total_rev = p_agg['rev'] + c_agg['rev']

        data.append({
            'conductor_id': conductor.id,
            'conductor_name': f'{conductor.first_name} {conductor.last_name}',
            'total_trips': trip_count,
            'total_passenger_tickets': p_agg['cnt'],
            'total_cargo_tickets': c_agg['cnt'],
            'total_revenue': total_rev,
            'total_expenses': e_agg['total'],
            'net': total_rev - e_agg['total'],
        })

    return Response(data)


