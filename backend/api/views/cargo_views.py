from django.core.exceptions import ValidationError as DjangoValidationError
from django.db import models, transaction
from django.utils import timezone
from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.exceptions import ValidationError, PermissionDenied

from api.models import CargoTicket, Trip
from api.serializers.cargo import CargoTicketSerializer, CargoTransitionSerializer
from api.permissions import RBACPermission, MatrixPermission, OfficeScopePermission


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

    required_actions = {
        'create': ['create_cargo_ticket'],
        'transition': ['transition_cargo_status'],
        'deliver': ['receive_cargo']
    }

    def get_permissions(self):
        from rest_framework.permissions import IsAuthenticated
        
        if self.action in ('create', 'transition', 'deliver'):
            return [IsAuthenticated(), MatrixPermission(), OfficeScopePermission()]
            
        # list / retrieve
        return [IsAuthenticated(), OfficeScopePermission()]

    def get_queryset(self):
        """Filter cargo by user scope to prevent data leaks."""
        user = self.request.user
        qs = CargoTicket.objects.select_related(
            'trip__origin_office', 'trip__destination_office',
        ).order_by('-id')

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
        Atomic cargo ticket creation.

        1. Lock Trip row via select_for_update (serializes concurrent creation).
        2. Set price from Trip tier snapshot.
        3. Auto-generate ticket number (safe: Trip row lock prevents race).
        """
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        trip_id = serializer.validated_data['trip'].id
        tier = serializer.validated_data['cargo_tier']

        if request.user.role not in ('admin', 'office_staff'):
            raise PermissionDenied('Only admin or office staff can create cargo tickets.')

        with transaction.atomic():
            # Row lock serializes concurrent ticket creation for this trip
            trip = Trip.objects.select_for_update().select_related('origin_office', 'destination_office').get(pk=trip_id)

            if request.user.role == 'office_staff' and request.user.office_id not in (
                trip.origin_office_id,
                trip.destination_office_id,
            ):
                raise PermissionDenied(
                    'Office staff can only create cargo tickets for trips linked to their office.'
                )

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
        """Shortcut for marking cargo as delivered (Bug #8 fix: single save)."""
        cargo = self.get_object()
        self._enforce_destination_office(request.user, cargo)

        # Bug #8 fix: set delivered_by/delivered_at BEFORE save to avoid
        # inconsistent state window where status=delivered but no delivery metadata
        valid_next = cargo.VALID_TRANSITIONS.get(cargo.status, [])
        if 'delivered' not in valid_next:
            raise ValidationError(
                f"Cannot transition from '{cargo.status}' to 'delivered'."
            )

        cargo.status = 'delivered'
        cargo.delivered_by = request.user
        cargo.delivered_at = timezone.now()
        cargo.version += 1
        cargo.save(skip_transition_check=True)

        return Response({
            'status': 'delivered',
            'delivered_at': cargo.delivered_at,
        })

    @staticmethod
    def _enforce_destination_office(user, cargo):
        """Only destination office staff can mark cargo as delivered."""
        if cargo.trip.status != 'completed':
            raise ValidationError('Cargo can only be delivered after trip completion at destination office.')

        if user.role not in ('admin',):
            if user.role != 'office_staff':
                raise PermissionDenied('Only office staff can mark cargo as delivered.')
            if not cargo.trip.destination_office_id:
                raise ValidationError('Trip has no destination office set.')
            if user.office_id != cargo.trip.destination_office_id:
                raise PermissionDenied('Only destination office can mark cargo as delivered.')
