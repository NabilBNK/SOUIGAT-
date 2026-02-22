from django.core.exceptions import ValidationError as DjangoValidationError
from django.db import models, transaction
from django.utils import timezone
from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.exceptions import ValidationError, PermissionDenied

from api.models import CargoTicket, Trip
from api.serializers.cargo import CargoTicketSerializer, CargoTransitionSerializer
from api.permissions import RBACPermission, OfficeScopePermission


class CargoTicketViewSet(viewsets.ModelViewSet):
    """
    Cargo ticket management.

    Create: atomic with Trip row lock, tier-based pricing from snapshot.
    Transition: enforces model state machine.
    Deliver: destination office staff only.
    """

    # Required by DRF router for basename resolution. Overridden by get_queryset().
    queryset = CargoTicket.objects.all().select_related(
        'trip__origin_office', 'trip__destination_office',
    )
    serializer_class = CargoTicketSerializer

    def get_permissions(self):
        perm = RBACPermission()
        perm.required_roles = ['admin', 'office_staff', 'conductor']
        return [perm, OfficeScopePermission()]

    def get_queryset(self):
        """Filter cargo by user scope to prevent data leaks."""
        user = self.request.user
        qs = CargoTicket.objects.select_related(
            'trip__origin_office', 'trip__destination_office',
        )

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
        Atomic cargo ticket creation.

        1. Lock Trip row (prevents race on ticket_number).
        2. Set price from Trip tier snapshot.
        3. Auto-generate ticket number.
        """
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        trip_id = serializer.validated_data['trip'].id
        tier = serializer.validated_data['cargo_tier']

        with transaction.atomic():
            trip = Trip.objects.select_for_update().get(pk=trip_id)

            valid_tiers = {'small', 'medium', 'large'}
            if tier not in valid_tiers:
                raise ValidationError(
                    f"Invalid cargo_tier '{tier}'. Must be one of: {', '.join(sorted(valid_tiers))}."
                )

            price_map = {
                'small': trip.cargo_small_price,
                'medium': trip.cargo_medium_price,
                'large': trip.cargo_large_price,
            }
            price = price_map[tier]

            count = CargoTicket.objects.filter(trip=trip).count()
            ticket_number = f'CT-{trip.id}-{count + 1:03d}'

            cargo = CargoTicket.objects.create(
                trip=trip,
                ticket_number=ticket_number,
                sender_name=serializer.validated_data['sender_name'],
                sender_phone=serializer.validated_data.get('sender_phone', ''),
                receiver_name=serializer.validated_data['receiver_name'],
                receiver_phone=serializer.validated_data.get('receiver_phone', ''),
                cargo_tier=tier,
                description=serializer.validated_data.get('description', ''),
                price=price,
                currency=trip.currency,
                payment_source=serializer.validated_data['payment_source'],
                created_by=request.user,
            )

        out = CargoTicketSerializer(cargo)
        return Response(out.data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=['post'])
    def transition(self, request, pk=None):
        """Generic state transition via model's transition_to()."""
        cargo = self.get_object()
        ser = CargoTransitionSerializer(data=request.data)
        ser.is_valid(raise_exception=True)

        new_status = ser.validated_data['new_status']
        reason = ser.validated_data.get('reason', '')

        if new_status == 'delivered':
            self._enforce_destination_office(request.user, cargo)

        try:
            cargo.transition_to(new_status, request.user, reason=reason)
        except DjangoValidationError as e:
            raise ValidationError(e.messages if hasattr(e, 'messages') else str(e))

        return Response({'status': cargo.status})

    @action(detail=True, methods=['post'])
    def deliver(self, request, pk=None):
        """Shortcut for marking cargo as delivered."""
        cargo = self.get_object()
        self._enforce_destination_office(request.user, cargo)

        try:
            cargo.transition_to('delivered', request.user)
        except DjangoValidationError as e:
            raise ValidationError(e.messages if hasattr(e, 'messages') else str(e))

        cargo.delivered_by = request.user
        cargo.delivered_at = timezone.now()
        cargo.save(skip_transition_check=True)

        return Response({
            'status': 'delivered',
            'delivered_at': cargo.delivered_at,
        })

    @staticmethod
    def _enforce_destination_office(user, cargo):
        """Only destination office staff can mark cargo as delivered."""
        if user.role not in ('admin',):
            if user.role != 'office_staff':
                raise PermissionDenied('Only office staff can mark cargo as delivered.')
            if not cargo.trip.destination_office_id:
                raise ValidationError('Trip has no destination office set.')
            if user.office_id != cargo.trip.destination_office_id:
                raise PermissionDenied('Only destination office can mark cargo as delivered.')
