import logging

from django.db import transaction
from rest_framework import viewsets, filters
from rest_framework.pagination import PageNumberPagination
from django_filters.rest_framework import DjangoFilterBackend

from api.filters.admin_filters import AuditLogFilter
from api.models import AuditLog, Bus, Office, PricingConfig, User
from api.permissions import RBACPermission
from api.services.firebase_admin import schedule_firebase_auth_user_sync
from api.serializers.admin_serializers import (
    AuditLogSerializer,
    BusManagementSerializer,
    OfficeManagementSerializer,
    PricingManagementSerializer,
    UserManagementSerializer,
)

logger = logging.getLogger(__name__)


class AdminViewSetMixin:
    """Common config for admin-only ViewSets."""

    def get_permissions(self):
        from rest_framework.permissions import IsAuthenticated
        perm = RBACPermission()
        perm.required_roles = ['admin']
        return [IsAuthenticated(), perm]


class UserManagementViewSet(AdminViewSetMixin, viewsets.ModelViewSet):
    """CRUD for users. No hard delete — use is_active=False."""

    queryset = User.objects.select_related('office').order_by('-date_joined')
    serializer_class = UserManagementSerializer
    filter_backends = [DjangoFilterBackend, filters.SearchFilter]
    filterset_fields = ['role', 'office', 'is_active', 'department']
    search_fields = ['phone', 'first_name', 'last_name']

    def perform_destroy(self, instance):
        """Soft deactivate instead of hard delete."""
        instance.is_active = False
        instance.save(update_fields=['is_active'])

        transaction.on_commit(
            lambda: schedule_firebase_auth_user_sync(
                user_id=instance.id,
                raw_password=None,
                allow_create=False,
            )
        )

        logger.info('User deactivated: %s by admin %s', instance.id, self.request.user.id)


class BusManagementViewSet(AdminViewSetMixin, viewsets.ModelViewSet):
    """CRUD for buses."""

    queryset = Bus.objects.select_related('office').order_by('plate_number')
    serializer_class = BusManagementSerializer
    filter_backends = [DjangoFilterBackend, filters.SearchFilter]
    filterset_fields = ['office', 'is_active']
    search_fields = ['plate_number']


class OfficeManagementViewSet(AdminViewSetMixin, viewsets.ModelViewSet):
    """CRUD for offices."""

    queryset = Office.objects.order_by('name')
    serializer_class = OfficeManagementSerializer
    filter_backends = [filters.SearchFilter]
    search_fields = ['name', 'city']


class PricingManagementViewSet(AdminViewSetMixin, viewsets.ModelViewSet):
    """CRUD for pricing. Cache invalidation handled by signals."""

    queryset = PricingConfig.objects.select_related(
        'origin_office', 'destination_office',
    ).order_by('-effective_from')
    serializer_class = PricingManagementSerializer
    filter_backends = [DjangoFilterBackend]
    filterset_fields = ['origin_office', 'destination_office', 'is_active']


class AuditLogPagination(PageNumberPagination):
    page_size = 100
    page_size_query_param = 'page_size'
    max_page_size = 500


class AuditLogViewSet(AdminViewSetMixin, viewsets.ReadOnlyModelViewSet):
    """Read-only audit log. Filterable by user, table, and date range."""

    queryset = AuditLog.objects.select_related('user').order_by('-created_at')
    serializer_class = AuditLogSerializer
    pagination_class = AuditLogPagination
    filter_backends = [DjangoFilterBackend, filters.SearchFilter]
    filterset_class = AuditLogFilter
    search_fields = ['table_name']
