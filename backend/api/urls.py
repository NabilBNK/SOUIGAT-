from django.urls import path
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.routers import DefaultRouter

from api.views import (
    admin_views, admin_management_views, auth, trip_views, ticket_views,
    cargo_views, expense_views, quarantine_views,
    sync_views, report_views, export_views,
)


@api_view(['GET'])
@permission_classes([AllowAny])
def health_check(request):
    return Response({'status': 'ok', 'service': 'souigat-api'})


router = DefaultRouter()
router.register(r'trips', trip_views.TripViewSet, basename='trip')
router.register(r'tickets', ticket_views.PassengerTicketViewSet, basename='ticket')
router.register(r'cargo', cargo_views.CargoTicketViewSet, basename='cargo')
router.register(r'expenses', expense_views.TripExpenseViewSet, basename='expense')
router.register(r'quarantine', quarantine_views.QuarantineViewSet, basename='quarantine')

# Admin management
router.register(r'admin/users', admin_management_views.UserManagementViewSet, basename='admin-user')
router.register(r'admin/buses', admin_management_views.BusManagementViewSet, basename='admin-bus')
router.register(r'admin/offices', admin_management_views.OfficeManagementViewSet, basename='admin-office')
router.register(r'admin/pricing', admin_management_views.PricingManagementViewSet, basename='admin-pricing')
router.register(r'admin/audit-log', admin_management_views.AuditLogViewSet, basename='admin-audit-log')

urlpatterns = [
    path('', health_check, name='api-health'),

    # Auth
    path('auth/login/', auth.login, name='login'),
    path('auth/logout/', auth.logout, name='logout'),
    path('auth/token/refresh/', auth.PlatformAwareTokenRefreshView.as_view(), name='token_refresh'),
    path('auth/me/', auth.me, name='me'),

    # Admin device management
    path('admin/users/<int:pk>/revoke-device/', admin_views.revoke_device, name='revoke_device'),
    path('admin/users/<int:pk>/rescue-device/', admin_views.rescue_device, name='rescue_device'),

    # Sync
    path('sync/batch/', sync_views.batch_sync, name='batch-sync'),

    # Reports
    path('reports/daily/', report_views.daily_report, name='report-daily'),
    path('reports/trip/<int:trip_id>/', report_views.trip_report, name='report-trip'),
    path('reports/route/', report_views.route_report, name='report-route'),
    path('reports/conductors/', report_views.conductor_report, name='report-conductors'),

    # Excel export
    path('exports/', export_views.trigger_export, name='export-trigger'),
    path('exports/<str:task_id>/status/', export_views.export_status, name='export-status'),
    path('exports/<str:task_id>/download/', export_views.export_download, name='export-download'),
] + router.urls
