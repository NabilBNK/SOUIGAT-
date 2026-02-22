from rest_framework import viewsets
from rest_framework.exceptions import PermissionDenied, ValidationError

from api.models import TripExpense
from api.serializers.expense import TripExpenseSerializer
from api.permissions import RBACPermission, TripStatusPermission


class TripExpenseViewSet(viewsets.ModelViewSet):
    """
    Trip expense management.

    Access: conductors only (for their own trips).
    Blocked: when trip is completed or cancelled (TripStatusPermission).
    """

    serializer_class = TripExpenseSerializer

    def get_permissions(self):
        perm = RBACPermission()
        perm.required_roles = ['conductor']
        return [perm, TripStatusPermission()]

    def get_queryset(self):
        """Conductors see only their own expenses. Includes select_related."""
        qs = TripExpense.objects.select_related('trip', 'created_by')
        if self.request.user.role == 'conductor':
            return qs.filter(created_by=self.request.user)
        return qs

    def perform_create(self, serializer):
        trip = serializer.validated_data['trip']

        if trip.conductor_id != self.request.user.id:
            raise PermissionDenied('You can only add expenses to your assigned trip.')

        if trip.status in ('completed', 'cancelled'):
            raise ValidationError(
                f'Cannot add expense. Trip status: {trip.status}'
            )

        serializer.save(
            created_by=self.request.user,
            currency=trip.currency,
        )
