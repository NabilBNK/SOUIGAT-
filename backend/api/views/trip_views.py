from django.db import models, transaction
from django.utils import timezone
from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.exceptions import ValidationError, PermissionDenied

from api.models import Trip, Bus, User
from api.serializers.trip import TripSerializer, TripListSerializer
from api.permissions import RBACPermission, OfficeScopePermission


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

    def get_permissions(self):
        if self.action in ('create', 'update', 'partial_update', 'destroy', 'cancel'):
            perm = RBACPermission()
            perm.required_roles = ['admin', 'office_staff']
            return [perm, OfficeScopePermission()]
        if self.action in ('start', 'complete'):
            perm = RBACPermission()
            perm.required_roles = ['conductor']
            return [perm]
        return [OfficeScopePermission()]

    def get_queryset(self):
        """
        Filter trips by user scope at DB level.

        Prevents N+1 permission checks and pagination data leaks.
        """
        user = self.request.user
        qs = Trip.objects.select_related(
            'origin_office', 'destination_office', 'conductor', 'bus',
        )

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
        """Enforce: staff can only create trips from their own office."""
        user = self.request.user
        if user.role == 'office_staff':
            origin = serializer.validated_data.get('origin_office')
            if origin.id != user.office_id:
                raise PermissionDenied(
                    'You can only create trips originating from your office.'
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

            if trip.conductor_id != request.user.id:
                raise PermissionDenied('You are not the assigned conductor.')

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

            if trip.conductor_id != request.user.id:
                raise PermissionDenied('You are not the assigned conductor.')

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
