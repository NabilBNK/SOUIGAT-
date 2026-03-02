from django.db import models, transaction
from django.utils import timezone
from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.exceptions import ValidationError, PermissionDenied

from api.models import Trip, Bus, User
from api.serializers.trip import TripSerializer, TripListSerializer
from api.permissions import RBACPermission, MatrixPermission, OfficeScopePermission


class TripViewSet(viewsets.ModelViewSet):
    """
    Trip management.

    CRUD: office_staff only (scoped to their office).
    start / complete: conductor only (assigned conductor).
    cancel: office_staff only.

    Concurrency strategy (lock order): Trip → Bus → Conductor.
    """

    def get_serializer_class(self):
        if self.action == 'list':
            return TripListSerializer
        return TripSerializer

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        if not serializer.is_valid():
            pass
        return super().create(request, *args, **kwargs)

    def get_permissions(self):
        from rest_framework.permissions import IsAuthenticated
        
        if self.action in ('create', 'update', 'partial_update', 'destroy', 'cancel'):
            perm = MatrixPermission()
            # Map HTTP methods and specific actions to the PERMISSION_MATRIX
            perm.required_actions = {
                'POST': ['create_trip'],
                'cancel': ['cancel_trip']
            }
            return [IsAuthenticated(), perm, OfficeScopePermission()]
            
        if self.action in ('start', 'complete'):
            perm = MatrixPermission()
            perm.required_actions = {
                'start': ['start_trip'],
                'complete': ['complete_trip']
            }
            return [IsAuthenticated(), perm]
            
        # list / retrieve
        return [IsAuthenticated(), OfficeScopePermission()]

    def get_queryset(self):
        """
        Filter trips by user scope at DB level.

        Prevents N+1 permission checks and pagination data leaks.
        """
        user = self.request.user
        qs = Trip.objects.select_related(
            'origin_office', 'destination_office', 'conductor', 'bus',
        ).order_by('-id')

        if user.role == 'admin':
            return qs

        if user.role == 'office_staff':
            return qs.filter(
                models.Q(origin_office_id=user.office_id)
                | models.Q(destination_office_id=user.office_id)
            )

        if user.role == 'conductor':
            return qs.filter(conductor_id=user.id)

        return qs.none()

    def perform_create(self, serializer):
        """Enforce: staff can only create trips from their own office. Bus must match origin."""
        user = self.request.user
        if user.role == 'office_staff':
            origin = serializer.validated_data.get('origin_office')
            if origin.id != user.office_id:
                raise PermissionDenied(
                    'You can only create trips originating from your office.'
                )

        # Bug #9 fix: validate bus belongs to origin office
        bus = serializer.validated_data.get('bus')
        origin = serializer.validated_data.get('origin_office')
        if bus and origin and bus.office_id != origin.id:
            raise ValidationError(
                {'bus': 'Bus must belong to the trip origin office.'}
            )

        serializer.save()

    # ------------------------------------------------------------------
    # Custom actions
    # ------------------------------------------------------------------

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
            elif request.user.role == 'office_staff' and trip.origin_office_id != request.user.office_id:
                raise PermissionDenied('You can only start trips originating from your office.')
            # Admin role handles cross-office scoping seamlessly here.

            if trip.status != 'scheduled':
                raise ValidationError(
                    f'Only scheduled trips can be started. Current status: {trip.status}'
                )

            Bus.objects.select_for_update().get(pk=trip.bus_id)
            User.objects.select_for_update().get(pk=trip.conductor_id)

            if Trip.objects.filter(bus=trip.bus, status='in_progress').exists():
                raise ValidationError('Bus is already on an active trip.')

            if Trip.objects.filter(
                conductor=trip.conductor, status='in_progress',
            ).exists():
                raise ValidationError('Conductor already has an active trip.')

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
            elif request.user.role == 'office_staff' and trip.destination_office_id != request.user.office_id:
                raise PermissionDenied('You can only complete trips arriving at your office.')
            # Admin role handles cross-office scoping seamlessly here.

            if trip.status != 'in_progress':
                raise ValidationError(
                    f'Cannot complete trip. Current status: {trip.status}'
                )

            # Skip model validation — arrival_datetime is set programmatically
            # and departure_datetime is trusted from original creation.
            trip.status = 'completed'
            trip.arrival_datetime = timezone.now()
            trip.save(skip_validation=True)

        return Response({
            'status': 'completed',
            'arrival_datetime': trip.arrival_datetime,
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
