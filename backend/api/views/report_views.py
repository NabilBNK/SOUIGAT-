from datetime import date

from django.core.cache import cache
from django.db import models
from django.db.models import Count, Q, Sum, Value
from django.db.models.functions import Coalesce
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework.exceptions import PermissionDenied

from api.models import CargoTicket, PassengerTicket, Trip, TripExpense


REPORT_ROLES = ('admin', 'office_staff')


def _check_report_perm(request):
    """Reports accessible by admin and office_staff."""
    if not request.user.is_authenticated:
        raise PermissionDenied('Authentication required.')
    if request.user.role not in REPORT_ROLES:
        raise PermissionDenied('Insufficient permissions for reports.')


def _parse_date_range(request):
    """Extract date_from / date_to from query params. Default: today."""
    today = date.today()
    date_from = request.query_params.get('date_from')
    date_to = request.query_params.get('date_to')

    try:
        date_from = date.fromisoformat(date_from) if date_from else today
        date_to = date.fromisoformat(date_to) if date_to else today
    except ValueError:
        date_from = today
        date_to = today

    return date_from, date_to


def _scope_trips(user, qs):
    """Filter trips by user scope."""
    if user.role == 'admin':
        return qs
    return qs.filter(
        Q(origin_office_id=user.office_id)
        | Q(destination_office_id=user.office_id)
    )


@api_view(['GET'])
def daily_report(request):
    """
    Daily revenue summary.

    ?date_from=YYYY-MM-DD&date_to=YYYY-MM-DD (default: today)
    ?office_id=N (admin only)
    """
    _check_report_perm(request)
    date_from, date_to = _parse_date_range(request)

    # Redis cache key: report:daily:{office}:{from}:{to}
    cache_key = f'report:daily:{request.user.role}:{request.user.office_id}:{date_from}:{date_to}'
    cached = cache.get(cache_key)
    if cached:
        return Response(cached)

    trips = Trip.objects.filter(
        departure_datetime__date__gte=date_from,
        departure_datetime__date__lte=date_to,
    )
    trips = _scope_trips(request.user, trips)

    trip_ids = list(trips.values_list('id', flat=True))

    passenger_rev = PassengerTicket.objects.filter(
        trip_id__in=trip_ids, status='active',
    ).aggregate(total=Coalesce(Sum('price'), Value(0)))['total']

    cargo_rev = CargoTicket.objects.filter(
        trip_id__in=trip_ids,
    ).exclude(status='cancelled').aggregate(
        total=Coalesce(Sum('price'), Value(0)),
    )['total']

    expense_total = TripExpense.objects.filter(
        trip_id__in=trip_ids,
    ).aggregate(total=Coalesce(Sum('amount'), Value(0)))['total']

    ticket_count = PassengerTicket.objects.filter(
        trip_id__in=trip_ids, status='active',
    ).count()

    cargo_count = CargoTicket.objects.filter(
        trip_id__in=trip_ids,
    ).exclude(status='cancelled').count()

    data = {
        'date_from': date_from,
        'date_to': date_to,
        'passenger_revenue': passenger_rev,
        'cargo_revenue': cargo_rev,
        'total_revenue': passenger_rev + cargo_rev,
        'expense_total': expense_total,
        'net': passenger_rev + cargo_rev - expense_total,
        'ticket_count': ticket_count,
        'cargo_count': cargo_count,
        'trip_count': len(trip_ids),
    }

    cache.set(cache_key, data, timeout=900)  # 15-min cache
    return Response(data)


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
        .values('description', 'amount', 'currency')
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
    """
    _check_report_perm(request)
    date_from, date_to = _parse_date_range(request)

    trips = Trip.objects.filter(
        departure_datetime__date__gte=date_from,
        departure_datetime__date__lte=date_to,
    )
    trips = _scope_trips(request.user, trips)

    routes = (
        trips.values('origin_office__name', 'destination_office__name')
        .annotate(
            trip_count=Count('id', distinct=True),
            passenger_revenue=Coalesce(
                Sum(
                    'passenger_tickets__price',
                    filter=Q(passenger_tickets__status='active'),
                ),
                Value(0),
            ),
            cargo_revenue=Coalesce(
                Sum(
                    'cargo_tickets__price',
                    filter=~Q(cargo_tickets__status='cancelled'),
                ),
                Value(0),
            ),
        )
        .order_by('-trip_count')
    )

    data = []
    for route in routes:
        p_rev = route['passenger_revenue']
        c_rev = route['cargo_revenue']
        data.append({
            'origin': route['origin_office__name'],
            'destination': route['destination_office__name'],
            'trip_count': route['trip_count'],
            'total_revenue': p_rev + c_rev,
            'passenger_revenue': p_rev,
            'cargo_revenue': c_rev,
        })

    return Response(data)
