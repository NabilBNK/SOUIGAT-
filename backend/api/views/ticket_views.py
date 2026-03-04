from django.db import models, transaction
from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.exceptions import ValidationError, PermissionDenied

from api.models import PassengerTicket, Trip
from api.serializers.ticket import PassengerTicketSerializer, PassengerTicketListSerializer
from api.permissions import RBACPermission, MatrixPermission, OfficeScopePermission


class PassengerTicketViewSet(viewsets.ModelViewSet):
    """
    Passenger ticket management.

    Create: Atomic capacity check with Trip row lock.
    Price: Frozen from Trip snapshot at creation.
    Access: office_staff + conductor.
    """

    # Required by DRF router for basename resolution. Overridden by get_queryset().
    queryset = PassengerTicket.objects.all().select_related('trip')

    def get_serializer_class(self):
        if self.action == 'list':
            return PassengerTicketListSerializer
        return PassengerTicketSerializer

    required_actions = {
        'create': ['create_passenger_ticket'],
        'cancel': ['cancel_trip']  # Keep cancel_trip role for now, usually office staff
    }

    def get_permissions(self):
        from rest_framework.permissions import IsAuthenticated
        
        if self.action == 'create':
            return [IsAuthenticated(), MatrixPermission(), OfficeScopePermission()]
            
        return [IsAuthenticated(), OfficeScopePermission()]

    def get_queryset(self):
        """Filter tickets by user scope and optional trip param."""
        user = self.request.user
        qs = PassengerTicket.objects.select_related('trip', 'trip__bus')

        trip_id = self.request.query_params.get('trip')
        if trip_id:
            qs = qs.filter(trip_id=trip_id)

        if user.role == 'admin':
            return qs

        if user.role == 'office_staff':
            return qs.filter(
                models.Q(trip__origin_office_id=user.office_id)
                | models.Q(trip__destination_office_id=user.office_id)
            )

        if user.role == 'conductor':
            return qs.filter(trip__conductor_id=user.id)

        return qs.none()

    def create(self, request, *args, **kwargs):
        """
        Atomic ticket creation.

        1. Lock Trip row via select_for_update (serializes concurrent creation).
        2. Validate status (scheduled / in_progress).
        3. Count active tickets vs bus capacity.
        4. Freeze price from Trip snapshot.
        5. Assign ticket_number from total count (safe: Trip row lock prevents race).
        """
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        trip_id = serializer.validated_data['trip'].id

        with transaction.atomic():
            # Row lock serializes concurrent ticket creation for this trip
            trip = Trip.objects.select_for_update().get(pk=trip_id)

            if trip.status not in ('scheduled', 'in_progress'):
                raise ValidationError(
                    'Trip is not open for ticket sales.'
                )

            if request.user.role == 'conductor' and trip.conductor_id != request.user.id:
                raise PermissionDenied('You can only sell tickets for your assigned trip.')

            active_count = PassengerTicket.objects.filter(
                trip=trip, status='active',
            ).count()
            if active_count >= trip.bus.capacity:
                raise ValidationError('Bus is at full capacity.')

            total_count = PassengerTicket.objects.filter(trip=trip).count()
            ticket_number = f'PT-{trip.id}-{total_count + 1:03d}'

            ticket = PassengerTicket.objects.create(
                trip=trip,
                ticket_number=ticket_number,
                passenger_name=serializer.validated_data.get('passenger_name', 'Walk-in'),
                price=trip.passenger_base_price,
                currency=trip.currency,
                payment_source=serializer.validated_data.get('payment_source', 'cash'),
                seat_number=serializer.validated_data.get('seat_number', ''),
                created_by=request.user,
            )

        out = PassengerTicketSerializer(ticket)
        return Response(out.data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=['post'])
    def cancel(self, request, pk=None):
        """Cancel a ticket. Only the trip's conductor or office staff can cancel."""
        with transaction.atomic():
            ticket = (
                PassengerTicket.objects
                .select_for_update()
                .select_related('trip')
                .get(pk=pk)
            )

            # Flaw #2 fix: enforce conductor ownership
            if request.user.role == 'conductor':
                if ticket.trip.conductor_id != request.user.id:
                    raise PermissionDenied(
                        'You can only cancel tickets for your assigned trip.'
                    )

            if ticket.status != 'active':
                raise ValidationError('Ticket is not active.')

            ticket.status = 'cancelled'
            ticket.save()

        return Response({'status': 'cancelled'})
