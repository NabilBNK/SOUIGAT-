import logging
from datetime import timedelta
from django.db import models, transaction
from django.db.models.deletion import ProtectedError
from django.utils import timezone
from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.exceptions import ValidationError, PermissionDenied

from api.models import Trip, Bus, User, Office
from api.serializers.settlement import SettlementPreviewSerializer
from api.serializers.trip import TripSerializer, TripListSerializer
from api.permissions import RBACPermission, MatrixPermission, OfficeScopePermission, IsAdminUser
from api.services import initiate_settlement_for_trip

logger = logging.getLogger(__name__)


def _completion_arrival_datetime(trip):
    """
    Ensure completion timestamps never violate the DB arrival>departure check.

    Trips may be started up to 30 minutes early, so a conductor can legitimately
    complete a trip before wall-clock time has passed the scheduled departure.
    In that case we clamp the recorded arrival just after departure.
    """
    now = timezone.now()
    minimum_arrival = trip.departure_datetime + timedelta(seconds=1)
    return max(now, minimum_arrival)


class TripViewSet(viewsets.ModelViewSet):
    """
    Trip management.

    CRUD: office_staff only (scoped to their office).
    start / complete: conductor only (assigned conductor).
    cancel: office_staff only.

    Concurrency strategy (lock order): Trip → Bus → Conductor.
    """

    required_actions = {
        'create': ['create_trip'],
        'update': ['create_trip'],          # Assuming update requires create_trip or similar (no exact update_trip in matrix)
        'partial_update': ['create_trip'],  
        'destroy': ['create_trip'],         # Or whatever is appropriate, though destroy isn't usually allowed
        'cancel': ['cancel_trip'],
        'start': ['start_trip'],
        'complete': ['complete_trip'],
        'force_complete': ['override_status'],
        'reference_data': ['create_trip'],
    }

    def get_serializer_class(self):
        if self.action == 'list':
            return TripListSerializer
        return TripSerializer


    def get_permissions(self):
        from rest_framework.permissions import IsAuthenticated

        if self.action == 'destroy':
            return [IsAuthenticated(), IsAdminUser()]
        
        if self.action in ('create', 'update', 'partial_update', 'destroy', 'cancel'):
            return [IsAuthenticated(), MatrixPermission(), OfficeScopePermission()]
            
        if self.action == 'reference_data':
            return [IsAuthenticated(), MatrixPermission()]

        if self.action in ('start', 'complete'):
            return [IsAuthenticated(), MatrixPermission()]
            
        if self.action == 'force_complete':
            return [IsAuthenticated(), IsAdminUser(), MatrixPermission()]
            
        # list / retrieve
        return [IsAuthenticated(), OfficeScopePermission()]

    def destroy(self, request, *args, **kwargs):
        """Admin can hard-delete only scheduled or cancelled trips."""
        trip = self.get_object()

        if trip.status not in ('scheduled', 'cancelled'):
            raise ValidationError({
                'error_code': 'INVALID_STATUS',
                'detail': f'Only scheduled or cancelled trips can be deleted. Current status: {trip.status}'
            })

        try:
            trip.delete()
        except ProtectedError as exc:
            raise ValidationError({
                'error_code': 'TRIP_HAS_RELATED_RECORDS',
                'detail': 'Trip cannot be deleted because related tickets or audit records still exist.'
            }) from exc

        return Response(status=status.HTTP_204_NO_CONTENT)

    def get_queryset(self):
        """
        Filter trips by user scope at DB level.

        Prevents N+1 permission checks and pagination data leaks.
        Supports ?status=scheduled,in_progress,cancelled for mobile TripStatusPullWorker.
        """
        user = self.request.user
        qs = Trip.objects.select_related(
            'origin_office', 'destination_office', 'conductor', 'bus',
        ).order_by('-id')

        if user.role == 'admin':
            pass  # No scope filter
        elif user.role == 'office_staff':
            qs = qs.filter(
                models.Q(origin_office_id=user.office_id)
                | models.Q(destination_office_id=user.office_id)
            )
        elif user.role == 'conductor':
            qs = qs.filter(conductor_id=user.id)
        else:
            return qs.none()

        # P0.3: Status filter for mobile TripStatusPullWorker
        # Usage: GET /api/trips/?status=scheduled,in_progress,cancelled
        status_param = self.request.query_params.get('status')
        if status_param:
            statuses = [s.strip() for s in status_param.split(',') if s.strip()]
            valid_statuses = {'scheduled', 'in_progress', 'completed', 'cancelled'}
            statuses = [s for s in statuses if s in valid_statuses]
            if statuses:
                qs = qs.filter(status__in=statuses)

        return qs

    def perform_create(self, serializer):
        """Enforce: staff can only create trips from their own office. Bus must match origin."""
        user = self.request.user
        if user.role == 'office_staff':
            origin = serializer.validated_data.get('origin_office')
            if origin.id != user.office_id:
                raise PermissionDenied(
                    'You can only create trips originating from your office.'
                )

        # Ensure the bus isn't double-booked on an overlapping schedule (+/- 4 hours)
        bus = serializer.validated_data.get('bus')
        departure = serializer.validated_data.get('departure_datetime')
        if bus and departure:
            window = timedelta(hours=4)
            overlapping = Trip.objects.filter(
                bus=bus,
                status__in=['scheduled', 'in_progress'],
                departure_datetime__gte=departure - window,
                departure_datetime__lte=departure + window
            ).exclude(pk=serializer.instance.pk if serializer.instance else None).exists()
            if overlapping:
                raise ValidationError({'bus': 'Bus is already assigned to an overlapping trip within a 4-hour window.'})

        # Ensure the conductor isn't double-booked on an overlapping schedule (+/- 4 hours)
        conductor = serializer.validated_data.get('conductor')
        if conductor and departure:
            window = timedelta(hours=4)
            overlapping_cond = Trip.objects.filter(
                conductor=conductor,
                status__in=['scheduled', 'in_progress'],
                departure_datetime__gte=departure - window,
                departure_datetime__lte=departure + window
            ).exclude(pk=serializer.instance.pk if serializer.instance else None).exists()
            if overlapping_cond:
                raise ValidationError({'conductor': 'Conductor is already assigned to an overlapping trip within a 4-hour window.'})

        serializer.save()

    # ------------------------------------------------------------------
    # Custom actions
    # ------------------------------------------------------------------

    @action(detail=False, methods=['get'], url_path='reference-data')
    def reference_data(self, request):
        """
        Read-only reference lists for trip creation UI.
        Avoids exposing admin-only CRUD endpoints to office staff.
        """
        user = request.user

        offices_qs = Office.objects.filter(is_active=True).order_by('name')
        conductors_qs = User.objects.filter(
            role='conductor',
            is_active=True,
        ).order_by('first_name', 'last_name')

        current_office_id = request.query_params.get('current_office_id')
        if user.role == 'office_staff':
            bus_office_id = user.office_id
            conductors_qs = conductors_qs.filter(office_id=user.office_id)
        else:
            try:
                bus_office_id = int(current_office_id) if current_office_id else None
            except (TypeError, ValueError):
                bus_office_id = None

        buses_qs = Bus.objects.filter(is_active=True)
        if bus_office_id:
            buses_qs = buses_qs.filter(office_id=bus_office_id)

        offices = [
            {'id': o.id, 'name': o.name, 'city': o.city}
            for o in offices_qs
        ]
        buses = [
            {
                'id': b.id,
                'plate_number': b.plate_number,
                'model': getattr(b, 'model', ''),
                'capacity': b.capacity,
                'office': b.office_id,
            }
            for b in buses_qs.order_by('plate_number')
        ]
        conductors = [
            {
                'id': c.id,
                'first_name': c.first_name,
                'last_name': c.last_name,
                'phone': c.phone,
                'office': c.office_id,
            }
            for c in conductors_qs
        ]

        return Response({
            'offices': offices,
            'buses': buses,
            'conductors': conductors,
        })

    @action(detail=True, methods=['post'])
    def start(self, request, pk=None):
        """
        Conductor starts a trip.

        Lock order: Trip → Bus → Conductor.
        Validates: scheduled status, no active trip for bus or conductor.
        """
        with transaction.atomic():
            trip = Trip.objects.select_for_update().get(pk=pk)

            if request.user.role == 'conductor' and trip.conductor_id != request.user.id:
                raise PermissionDenied('You are not the assigned conductor.')
            # MatrixPermission handles blocking office_staff from this action.
            # Admin role handles cross-office scoping seamlessly here.

            now = timezone.now()
            # Enforce start-time constraint: cannot start trip more than 30 mins before departure
            if trip.departure_datetime > now + timedelta(minutes=30):
                raise ValidationError({
                    'error_code': 'TOO_EARLY',
                    'detail': f'Cannot start trip more than 30 minutes before scheduled departure ({trip.departure_datetime}).'
                })

            if trip.status != 'scheduled':
                raise ValidationError(
                    f'Only scheduled trips can be started. Current status: {trip.status}'
                )

            Bus.objects.select_for_update().get(pk=trip.bus_id)
            User.objects.select_for_update().get(pk=trip.conductor_id)

            if Trip.objects.filter(bus=trip.bus, status='in_progress').exists():
                raise ValidationError({
                    'error_code': 'BUS_BUSY',
                    'detail': 'Bus is already on an active trip.'
                })

            if Trip.objects.filter(
                conductor=trip.conductor, status='in_progress',
            ).exists():
                raise ValidationError({
                    'error_code': 'CONDUCTOR_BUSY',
                    'detail': 'Conductor already has an active trip.'
                })

            # Skip model validation — only status is changing programmatically,
            # not user-supplied data. Avoids re-running model clean().
            trip.status = 'in_progress'
            trip.save(skip_validation=True)

        return Response({'status': 'in_progress'})

    @action(detail=True, methods=['post'])
    def complete(self, request, pk=None):
        """Conductor completes a trip."""
        with transaction.atomic():
            trip = Trip.objects.select_for_update().get(pk=pk)

            if request.user.role == 'conductor' and trip.conductor_id != request.user.id:
                raise PermissionDenied('You are not the assigned conductor.')
            # MatrixPermission handles blocking office_staff from this action.
            # Admin role handles cross-office scoping seamlessly here.

            if trip.status != 'in_progress':
                raise ValidationError({
                    'error_code': 'INVALID_STATUS',
                    'detail': f'Cannot complete trip. Current status: {trip.status}'
                })

            # Skip model validation — arrival_datetime is set programmatically
            # and departure_datetime is trusted from original creation.
            trip.status = 'completed'
            trip.arrival_datetime = _completion_arrival_datetime(trip)
            trip.save(skip_validation=True)
            settlement_preview = None
            settlement_preview_error = None
            try:
                settlement, _created = initiate_settlement_for_trip(trip)
                settlement_preview = SettlementPreviewSerializer.from_settlement(settlement)
            except Exception:
                logger.error(
                    'Settlement initiation failed during trip completion for trip %s',
                    trip.id,
                    exc_info=True,
                )
                settlement_preview_error = 'settlement_init_failed'

        return Response({
            'status': 'completed',
            'arrival_datetime': trip.arrival_datetime,
            'settlement_preview': settlement_preview,
            'settlement_preview_error': settlement_preview_error,
        })

    @action(detail=True, methods=['post'])
    def force_complete(self, request, pk=None):
        """
        Admin action to force complete a stuck trip.
        Reconciliation Gate: Checks for unsynced tickets/expenses.
        Requires a 'force_reason'.
        """
        force_reason = request.data.get('force_reason')
        if not force_reason:
            raise ValidationError({'error_code': 'MISSING_REASON', 'detail': 'force_reason is required.'})

        with transaction.atomic():
            trip = Trip.objects.select_for_update().get(pk=pk)

            if trip.status != 'in_progress':
                raise ValidationError({
                    'error_code': 'INVALID_STATUS',
                    'detail': f'Only in_progress trips can be force completed. Current status: {trip.status}'
                })

            from api.models import CargoTicket, PassengerTicket, TripExpense

            has_unsynced_tickets = PassengerTicket.objects.filter(
                trip_id=trip.id, synced_at__isnull=True,
            ).exists() or CargoTicket.objects.filter(
                trip_id=trip.id, synced_at__isnull=True,
            ).exists()
            has_unsynced_expenses = TripExpense.objects.filter(trip_id=trip.id, synced_at__isnull=True).exists()

            if has_unsynced_tickets or has_unsynced_expenses:
                raise ValidationError({
                    'error_code': 'TRIP_HAS_PENDING_SYNC', 
                    'detail': 'Cannot force-complete: records not yet synced from conductor device.'
                })

            trip.status = 'completed'
            trip.arrival_datetime = _completion_arrival_datetime(trip)
            trip.save(skip_validation=True)
            try:
                settlement, _created = initiate_settlement_for_trip(trip)
                settlement_preview = SettlementPreviewSerializer.from_settlement(settlement)
            except Exception as exc:
                logger.error(
                    'Settlement initiation failed during force-complete for trip %s',
                    trip.id,
                    exc_info=True,
                )
                raise ValidationError({
                    'error_code': 'SETTLEMENT_INIT_FAILED',
                    'detail': 'Trip force-complete aborted because settlement initiation failed.',
                }) from exc

            from api.models import AuditLog
            AuditLog.objects.create(
                action='override',
                user=request.user,
                table_name='api_trip',
                record_id=trip.id,
                old_values={'status': 'in_progress'},
                new_values={'status': 'completed', 'admin_note': force_reason}
            )

        return Response({
            'status': 'completed',
            'arrival_datetime': trip.arrival_datetime,
            'settlement_preview': settlement_preview,
            'settlement_preview_error': None,
        })

    @action(detail=True, methods=['post'])
    def cancel(self, request, pk=None):
        """Office staff cancels a scheduled trip."""
        with transaction.atomic():
            trip = Trip.objects.select_for_update().get(pk=pk)

            if trip.status not in ('scheduled',):
                raise ValidationError(
                    f'Only scheduled trips can be cancelled. Current status: {trip.status}'
                )

            # Skip model validation — only status is changing programmatically.
            trip.status = 'cancelled'
            trip.save(skip_validation=True)

        return Response({'status': 'cancelled'})
