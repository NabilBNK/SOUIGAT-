import logging

from django.db import transaction
from django.db.models.deletion import ProtectedError
from rest_framework import viewsets, filters, status
from rest_framework.decorators import action
from rest_framework.pagination import PageNumberPagination
from rest_framework.exceptions import ValidationError
from rest_framework.response import Response
from django_filters.rest_framework import DjangoFilterBackend

from api.filters.admin_filters import AuditLogFilter
from api.models import (
    AuditLog,
    Bus,
    Office,
    PricingConfig,
    RouteTemplate,
    RouteTemplateSegmentTariff,
    RouteTemplateStop,
    User,
)
from api.permissions import RBACPermission
from api.services.firebase_admin import (
    schedule_firebase_auth_user_delete,
    schedule_firebase_auth_user_sync,
)
from api.services.route_templates import create_reverse_template
from api.serializers.admin_serializers import (
    AuditLogSerializer,
    BusManagementSerializer,
    OfficeManagementSerializer,
    PricingManagementSerializer,
    RouteTemplateManagementSerializer,
    RouteTemplateSegmentTariffSerializer,
    RouteTemplateStopSerializer,
    UserManagementSerializer,
)

logger = logging.getLogger(__name__)
PROTECTED_ADMIN_PHONE = '0500000001'


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

    @staticmethod
    def _is_protected_admin(user: User) -> bool:
        return bool(user.phone == PROTECTED_ADMIN_PHONE or user.is_superuser)

    def update(self, request, *args, **kwargs):
        instance = self.get_object()
        if self._is_protected_admin(instance):
            requested_active = request.data.get('is_active')
            if requested_active in (False, 'false', 'False', 0, '0'):
                return Response(
                    {'detail': 'Le compte administrateur principal ne peut pas etre desactive.'},
                    status=status.HTTP_400_BAD_REQUEST,
                )
        return super().update(request, *args, **kwargs)

    def partial_update(self, request, *args, **kwargs):
        return self.update(request, *args, **kwargs)

    def perform_destroy(self, instance):
        """Soft deactivate instead of hard delete."""
        if self._is_protected_admin(instance):
            raise ValidationError('Le compte administrateur principal ne peut pas etre supprime.')

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

    @action(detail=False, methods=['post'], url_path='bulk-delete')
    def bulk_delete(self, request):
        ids = request.data.get('ids', [])
        hard = bool(request.data.get('hard', False))

        if not isinstance(ids, list) or not ids:
            return Response({'detail': 'ids list is required.'}, status=status.HTTP_400_BAD_REQUEST)

        parsed_ids: list[int] = []
        for raw in ids:
            try:
                parsed_ids.append(int(raw))
            except (TypeError, ValueError):
                return Response({'detail': 'ids must contain integers only.'}, status=status.HTTP_400_BAD_REQUEST)

        queryset = User.objects.filter(id__in=parsed_ids)
        deleted = 0
        deactivated = 0
        errors: list[dict[str, str | int]] = []

        for user in queryset:
            if self._is_protected_admin(user):
                errors.append({'id': user.id, 'detail': 'Le compte administrateur principal est protege et ne peut pas etre supprime.'})
                continue

            if user.id == request.user.id and hard:
                errors.append({'id': user.id, 'detail': 'Cannot hard delete your current account.'})
                continue

            try:
                if hard:
                    deleted_user_id = user.id
                    deleted_user_phone = user.phone
                    user.delete()
                    deleted += 1
                    transaction.on_commit(
                        lambda uid=deleted_user_id, phone=deleted_user_phone: schedule_firebase_auth_user_delete(
                            user_id=uid,
                            phone=phone,
                        )
                    )
                    logger.info('User hard-deleted: %s by admin %s', user.id, request.user.id)
                else:
                    if user.is_active:
                        user.is_active = False
                        user.save(update_fields=['is_active'])
                        deactivated += 1
                        transaction.on_commit(
                            lambda uid=user.id: schedule_firebase_auth_user_sync(
                                user_id=uid,
                                raw_password=None,
                                allow_create=False,
                            )
                        )
            except Exception as exc:
                errors.append({'id': user.id, 'detail': str(exc)})

        return Response(
            {
                'processed': len(parsed_ids),
                'deleted': deleted,
                'deactivated': deactivated,
                'errors': errors,
            },
            status=status.HTTP_200_OK,
        )


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

    @action(detail=False, methods=['post'], url_path='bulk-delete')
    def bulk_delete(self, request):
        ids = request.data.get('ids', [])

        if not isinstance(ids, list) or not ids:
            return Response({'detail': 'ids list is required.'}, status=status.HTTP_400_BAD_REQUEST)

        parsed_ids: list[int] = []
        for raw in ids:
            try:
                parsed_ids.append(int(raw))
            except (TypeError, ValueError):
                return Response({'detail': 'ids must contain integers only.'}, status=status.HTTP_400_BAD_REQUEST)

        queryset = Office.objects.filter(id__in=parsed_ids)
        deleted = 0
        errors: list[dict[str, str | int]] = []

        for office in queryset:
            try:
                office.delete()
                deleted += 1
                logger.info('Office hard-deleted: %s by admin %s', office.id, request.user.id)
            except ProtectedError:
                errors.append({'id': office.id, 'detail': 'Office is referenced by other records and cannot be deleted.'})
            except Exception as exc:
                errors.append({'id': office.id, 'detail': str(exc)})

        return Response(
            {
                'processed': len(parsed_ids),
                'deleted': deleted,
                'errors': errors,
            },
            status=status.HTTP_200_OK,
        )


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


class RouteTemplateManagementViewSet(AdminViewSetMixin, viewsets.ModelViewSet):
    """Admin CRUD for ordered route templates."""

    queryset = RouteTemplate.objects.select_related(
        "start_office",
        "end_office",
        "source_template",
    ).prefetch_related("stops__office", "segment_tariffs__from_stop", "segment_tariffs__to_stop").order_by("name")
    serializer_class = RouteTemplateManagementSerializer
    filter_backends = [DjangoFilterBackend, filters.SearchFilter]
    filterset_fields = ["direction", "is_active", "start_office", "end_office"]
    search_fields = ["name", "code"]

    @action(detail=True, methods=["post"], url_path="create-reverse")
    def create_reverse(self, request, pk=None):
        template = self.get_object()
        reverse_template = create_reverse_template(template)
        serializer = self.get_serializer(reverse_template)
        return Response(serializer.data, status=status.HTTP_201_CREATED)


class RouteTemplateStopManagementViewSet(AdminViewSetMixin, viewsets.ModelViewSet):
    """Admin CRUD for template ordered stops."""

    queryset = RouteTemplateStop.objects.select_related("route_template", "office").order_by("route_template_id", "stop_order")
    serializer_class = RouteTemplateStopSerializer
    filter_backends = [DjangoFilterBackend]
    filterset_fields = ["route_template", "office"]

    def get_queryset(self):
        qs = super().get_queryset()
        template_id = self.request.query_params.get("route_template")
        if template_id:
            qs = qs.filter(route_template_id=template_id)
        return qs


class RouteTemplateSegmentTariffManagementViewSet(AdminViewSetMixin, viewsets.ModelViewSet):
    """Admin CRUD for adjacent passenger segment tariffs."""

    queryset = RouteTemplateSegmentTariff.objects.select_related(
        "route_template", "from_stop", "to_stop"
    ).order_by("route_template_id", "from_stop__stop_order")
    serializer_class = RouteTemplateSegmentTariffSerializer
    filter_backends = [DjangoFilterBackend]
    filterset_fields = ["route_template", "is_active", "currency"]

    def get_queryset(self):
        qs = super().get_queryset()
        template_id = self.request.query_params.get("route_template")
        if template_id:
            qs = qs.filter(route_template_id=template_id)
        return qs
