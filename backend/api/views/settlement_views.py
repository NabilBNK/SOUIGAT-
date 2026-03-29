import logging
from datetime import date

from django.conf import settings
from django.db import transaction
from django.db.models import Case, IntegerField, Value, When
from django.http import Http404
from django.utils import timezone
from rest_framework.decorators import api_view
from rest_framework.exceptions import PermissionDenied, ValidationError
from rest_framework.pagination import PageNumberPagination
from rest_framework.response import Response

from api.models import AuditLog, Settlement, Trip
from api.permissions import get_cached_user_permissions
from api.serializers.settlement import (
    SettlementDisputeSerializer,
    SettlementListSerializer,
    SettlementRecordSerializer,
    SettlementResolveSerializer,
    SettlementSerializer,
)
from api.services import (
    get_trip_mirror_completion,
    initiate_settlement_for_trip,
    serialize_settlement_audit,
    upsert_trip_report_snapshots_for_trip,
)

logger = logging.getLogger(__name__)


class SettlementPagination(PageNumberPagination):
    page_size = 20
    page_size_query_param = 'page_size'
    max_page_size = 100


def _require_permission(request, required_perm):
    if not request.user.is_authenticated:
        raise PermissionDenied('Authentication required.')
    if required_perm not in get_cached_user_permissions(request):
        raise PermissionDenied('Insufficient permissions for settlements.')


def _office_can_access_settlements(user):
    return user.role == 'admin' or (
        user.role == 'office_staff'
        and getattr(user, 'department', None) != 'cargo'
        and user.office_id is not None
    )


def _scoped_settlements(request):
    base = Settlement.objects.select_related(
        'trip__origin_office', 'trip__destination_office', 'office', 'conductor', 'settled_by',
    )
    if request.user.role == 'admin':
        return base
    if _office_can_access_settlements(request.user):
        return base.filter(office_id=request.user.office_id)
    return base.none()


def _get_scoped_settlement(request, trip_id, for_update=False):
    queryset = _scoped_settlements(request)
    if for_update:
        queryset = queryset.select_for_update(of=('self',))
    try:
        return queryset.get(trip_id=trip_id)
    except Settlement.DoesNotExist as exc:
        raise Http404 from exc


def _get_trip_for_initiate(request, trip_id):
    try:
        trip = Trip.objects.select_for_update().select_related(
            'origin_office', 'destination_office', 'conductor',
        ).get(pk=trip_id)
    except Trip.DoesNotExist as exc:
        raise Http404 from exc

    if request.user.role == 'admin':
        return trip

    if not _office_can_access_settlements(request.user):
        raise PermissionDenied('Insufficient permissions for settlements.')

    if trip.destination_office_id != request.user.office_id:
        raise PermissionDenied('You can only initiate settlements for your destination office.')

    return trip


def _parse_date_param(raw_value, label):
    if not raw_value:
        return None
    try:
        return date.fromisoformat(raw_value)
    except ValueError as exc:
        raise ValidationError({label: 'Use YYYY-MM-DD format.'}) from exc


def _log_settlement_audit(request, action, settlement, old_values=None, new_values=None):
    AuditLog.objects.create(
        user=request.user,
        action=action,
        table_name='settlements',
        record_id=settlement.id,
        old_values=old_values,
        new_values=new_values,
        ip_address=request.META.get('REMOTE_ADDR'),
    )


def _recompute_discrepancy(settlement):
    if settlement.actual_cash_received is None:
        settlement.discrepancy_amount = None
        return
    settlement.discrepancy_amount = (
        settlement.actual_cash_received
        - settlement.actual_expenses_reimbursed
        - settlement.net_cash_expected
    )


def _ordered_settlements(queryset):
    return queryset.annotate(
        status_rank=Case(
            When(status=Settlement.STATUS_DISPUTED, then=Value(0)),
            default=Value(1),
            output_field=IntegerField(),
        )
    ).order_by('status_rank', '-created_at')


@api_view(['POST'])
def initiate_settlement(request, trip_id):
    _require_permission(request, 'record_settlement')

    with transaction.atomic():
        trip = _get_trip_for_initiate(request, trip_id)
        if trip.status != 'completed':
            mirror_completed, mirror_arrival = get_trip_mirror_completion(trip.id)
            if not mirror_completed:
                raise ValidationError({'detail': 'Only completed trips can be settled.'})

            update_fields = ['status', 'updated_at']
            trip.status = 'completed'
            if mirror_arrival and trip.arrival_datetime is None:
                trip.arrival_datetime = mirror_arrival
                update_fields.append('arrival_datetime')
            trip.save(update_fields=update_fields)
            logger.info(
                'Settlement initiation auto-reconciled trip %s to completed from Firebase mirror.',
                trip.id,
            )

        settlement, created = initiate_settlement_for_trip(trip)
        if created:
            _log_settlement_audit(
                request,
                'create',
                settlement,
                old_values=None,
                new_values=serialize_settlement_audit(settlement),
            )

    serializer = SettlementSerializer(settlement)
    return Response(
        serializer.data,
        status=201 if created else 200,
    )


@api_view(['GET'])
def settlement_detail(request, trip_id):
    _require_permission(request, 'view_settlements')
    settlement = _get_scoped_settlement(request, trip_id)
    return Response(SettlementSerializer(settlement).data)


@api_view(['PATCH'])
def record_settlement(request, trip_id):
    _require_permission(request, 'record_settlement')
    serializer = SettlementRecordSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    with transaction.atomic():
        settlement = _get_scoped_settlement(request, trip_id, for_update=True)
        if settlement.status not in (Settlement.STATUS_PENDING, Settlement.STATUS_PARTIAL):
            raise ValidationError({'detail': 'Only pending or partial settlements can be recorded.'})

        old_values = serialize_settlement_audit(settlement)
        settlement.actual_cash_received = serializer.validated_data['actual_cash_received']
        settlement.actual_expenses_reimbursed = serializer.validated_data.get(
            'actual_expenses_reimbursed', 0,
        )
        if 'notes' in serializer.validated_data:
            settlement.notes = serializer.validated_data.get('notes') or ''
        settlement.settled_by = request.user
        _recompute_discrepancy(settlement)

        if abs(settlement.discrepancy_amount or 0) <= settings.SETTLEMENT_TOLERANCE_DZD:
            settlement.status = Settlement.STATUS_SETTLED
            settlement.settled_at = timezone.now()
        else:
            settlement.status = Settlement.STATUS_PARTIAL
            settlement.settled_at = None

        settlement.save()
        upsert_trip_report_snapshots_for_trip(settlement.trip)
        _log_settlement_audit(
            request,
            'update',
            settlement,
            old_values=old_values,
            new_values=serialize_settlement_audit(settlement),
        )

    return Response(SettlementSerializer(settlement).data)


@api_view(['PATCH'])
def dispute_settlement(request, trip_id):
    _require_permission(request, 'record_settlement')
    serializer = SettlementDisputeSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    with transaction.atomic():
        settlement = _get_scoped_settlement(request, trip_id, for_update=True)
        if settlement.status not in (Settlement.STATUS_PENDING, Settlement.STATUS_PARTIAL):
            raise ValidationError({'detail': 'Only pending or partial settlements can be disputed.'})

        old_values = serialize_settlement_audit(settlement)
        settlement.status = Settlement.STATUS_DISPUTED
        settlement.dispute_reason = serializer.validated_data['dispute_reason']
        if 'notes' in serializer.validated_data:
            settlement.notes = serializer.validated_data.get('notes') or ''
        settlement.disputed_at = timezone.now()
        settlement.save()
        _log_settlement_audit(
            request,
            'update',
            settlement,
            old_values=old_values,
            new_values=serialize_settlement_audit(settlement),
        )

    return Response(SettlementSerializer(settlement).data)


@api_view(['PATCH'])
def resolve_settlement(request, trip_id):
    _require_permission(request, 'resolve_settlement')
    serializer = SettlementResolveSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    with transaction.atomic():
        settlement = _get_scoped_settlement(request, trip_id, for_update=True)
        if settlement.status != Settlement.STATUS_DISPUTED:
            raise ValidationError({'detail': 'Only disputed settlements can be resolved.'})

        old_values = serialize_settlement_audit(settlement)
        if 'actual_cash_received' in serializer.validated_data:
            settlement.actual_cash_received = serializer.validated_data['actual_cash_received']
        if 'actual_expenses_reimbursed' in serializer.validated_data:
            settlement.actual_expenses_reimbursed = serializer.validated_data['actual_expenses_reimbursed']
        if settlement.actual_cash_received is None:
            raise ValidationError({'actual_cash_received': 'Required to resolve a settlement.'})

        settlement.notes = serializer.validated_data['notes']
        settlement.settled_by = request.user
        settlement.status = Settlement.STATUS_SETTLED
        settlement.resolved_at = timezone.now()
        settlement.settled_at = settlement.settled_at or timezone.now()
        _recompute_discrepancy(settlement)
        settlement.save()
        upsert_trip_report_snapshots_for_trip(settlement.trip)
        _log_settlement_audit(
            request,
            'override',
            settlement,
            old_values=old_values,
            new_values=serialize_settlement_audit(settlement),
        )

    return Response(SettlementSerializer(settlement).data)


@api_view(['GET'])
def list_settlements(request):
    _require_permission(request, 'view_settlements')

    if not _office_can_access_settlements(request.user):
        raise PermissionDenied('Insufficient permissions for settlements.')

    queryset = _scoped_settlements(request)
    status_param = request.query_params.get('status')
    if status_param:
        queryset = queryset.filter(status=status_param)

    conductor_id = request.query_params.get('conductor_id')
    if conductor_id:
        queryset = queryset.filter(conductor_id=conductor_id)

    if request.user.role == 'admin':
        office_id = request.query_params.get('office_id')
        if office_id:
            queryset = queryset.filter(office_id=office_id)

    date_from = _parse_date_param(request.query_params.get('date_from'), 'date_from')
    if date_from:
        queryset = queryset.filter(created_at__date__gte=date_from)

    date_to = _parse_date_param(request.query_params.get('date_to'), 'date_to')
    if date_to:
        queryset = queryset.filter(created_at__date__lte=date_to)

    queryset = _ordered_settlements(queryset)
    paginator = SettlementPagination()
    page = paginator.paginate_queryset(queryset, request)
    serializer = SettlementListSerializer(page, many=True)
    return paginator.get_paginated_response(serializer.data)


@api_view(['GET'])
def pending_settlements(request):
    _require_permission(request, 'view_settlements')

    if not _office_can_access_settlements(request.user):
        raise PermissionDenied('Insufficient permissions for settlements.')

    queryset = _scoped_settlements(request).filter(
        status=Settlement.STATUS_PENDING,
    ).order_by('created_at')
    paginator = SettlementPagination()
    page = paginator.paginate_queryset(queryset, request)
    serializer = SettlementListSerializer(page, many=True)
    return paginator.get_paginated_response(serializer.data)
