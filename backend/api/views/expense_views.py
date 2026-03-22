from rest_framework import viewsets
from rest_framework.exceptions import PermissionDenied, ValidationError
from django.db.models import Q

from api.models import TripExpense
from api.serializers.expense import TripExpenseSerializer
from api.permissions import MatrixPermission, OfficeScopePermission, TripStatusPermission


class TripExpenseViewSet(viewsets.ModelViewSet):
    """
    Trip expense management.

    Access: conductors only (for their own trips).
    Blocked: when trip is completed or cancelled (TripStatusPermission).
    """

    serializer_class = TripExpenseSerializer

    required_actions = {
        'create': ['create_expense'],
        'update': ['create_expense'],
        'partial_update': ['create_expense'],
        'destroy': ['create_expense']
    }

    def get_permissions(self):
        from rest_framework.permissions import IsAuthenticated
        
        if self.action in ('create', 'update', 'partial_update', 'destroy'):
            return [IsAuthenticated(), MatrixPermission(), TripStatusPermission()]
            
        # Read access is still scoped by queryset and object permissions.
        return [IsAuthenticated(), OfficeScopePermission()]

    def get_queryset(self):
        """Scope expenses to the caller's effective office or trip ownership."""
        qs = TripExpense.objects.select_related(
            'trip', 'created_by', 'trip__origin_office', 'trip__destination_office',
        ).order_by('-created_at', '-id')
        if self.request.user.role == 'admin':
            return qs
        if self.request.user.role == 'office_staff':
            office_id = self.request.user.office_id
            if office_id is None:
                return qs.none()
            return qs.filter(
                Q(trip__origin_office_id=office_id)
                | Q(trip__destination_office_id=office_id)
            )
        if self.request.user.role == 'conductor':
            return qs.filter(created_by=self.request.user)
        return qs.none()

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
