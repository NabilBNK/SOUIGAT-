import logging

from django.core.cache import cache
from rest_framework.exceptions import AuthenticationFailed
from rest_framework.permissions import BasePermission

logger = logging.getLogger(__name__)


# ---------- Single-source permission matrix ----------

PERMISSION_MATRIX = {
    'admin': [
        'view_all_offices', 'manage_users', 'manage_buses',
        'manage_pricing', 'view_audit_log', 'override_status',
        'approve_quarantine', 'revoke_devices',
    ],
    'office_staff': {
        'all': [
            'create_trip', 'view_office_trips', 'cancel_trip',
            'create_passenger_ticket', 'create_cargo_ticket',
            'receive_cargo', 'view_office_reports', 'export_excel',
        ],
        'cargo': [
            'view_office_trips', 'create_cargo_ticket',
            'receive_cargo', 'transition_cargo_status',
        ],
        'passenger': [
            'view_office_trips', 'create_passenger_ticket',
        ],
    },
    'conductor': [
        'start_trip', 'complete_trip', 'create_passenger_ticket',
        'create_cargo_ticket', 'create_expense', 'view_own_trip',
        'sync_batch',
    ],
    'driver': ['view_own_trip'],
}


def get_user_permissions(user):
    """Return flat permission list for a user based on role + department."""
    if user.role == 'admin':
        return PERMISSION_MATRIX['admin']
    if user.role == 'office_staff':
        dept = user.department or 'all'
        return PERMISSION_MATRIX['office_staff'].get(dept, [])
    return PERMISSION_MATRIX.get(user.role, [])


# ---------- Permission classes ----------

class RBACPermission(BasePermission):
    """
    Checks request.user.role against view.required_roles.

    Usage on view:
        required_roles = ['admin', 'office_staff']
    """

    def has_permission(self, request, view):
        required_roles = getattr(view, 'required_roles', None) or getattr(self, 'required_roles', None)
        if not required_roles:
            return True
        return request.user.role in required_roles


class DepartmentPermission(BasePermission):
    """
    Blocks cross-department access for office_staff.

    Usage on view:
        required_department = 'cargo'  # or 'passenger'
    """

    def has_permission(self, request, view):
        required_dept = getattr(view, 'required_department', None)
        if not required_dept:
            return True
        if request.user.role != 'office_staff':
            return True  # Non-office roles not subject to dept filter
        user_dept = request.user.department or 'all'
        return user_dept in ('all', required_dept)


class OfficeScopePermission(BasePermission):
    """
    Filters queryset to user's office. Admin bypasses.

    Works as object-level permission. Views should call
    `self.check_object_permissions(request, obj)`.
    """

    def has_object_permission(self, request, view, obj):
        if request.user.role == 'admin':
            return True
        
        # Handle Trip objects (check origin or destination)
        if hasattr(obj, 'origin_office_id'):
            return (obj.origin_office_id == request.user.office_id or
                    obj.destination_office_id == request.user.office_id)
            
        obj_office = getattr(obj, 'office', None) or getattr(obj, 'origin', None)
        if obj_office is None:
            return True
        return obj_office.id == request.user.office_id


class TripStatusPermission(BasePermission):
    """Blocks write actions on completed or cancelled trips."""

    BLOCKED_STATUSES = ('completed', 'cancelled')

    def has_object_permission(self, request, view, obj):
        if request.method in ('GET', 'HEAD', 'OPTIONS'):
            return True
        trip = getattr(obj, 'trip', obj)
        trip_status = getattr(trip, 'status', None)
        if trip_status in self.BLOCKED_STATUSES:
            return False
        return True


class DeviceBoundPermission(BasePermission):
    """
    Enforces device binding for mobile users. Runs AFTER JWT auth.

    Checks:
    1. Device revocation (Redis key `revoked_device:{id}`)
    2. Device binding match (JWT device vs DB device)
    3. Grace period (30-min window after device switch)

    Fail-closed on Redis errors.

    Performance: 1-2 Redis calls per authenticated request.
    Current load: ~400 calls/min (8 conductors × 50 req/min).
    Redis capacity: 100K ops/sec.
    Bottleneck threshold: ~5000 concurrent users.
    """

    def has_permission(self, request, view):
        # 0. Allow anonymous users (handled by IsAuthenticated)
        if not request.user or not request.user.is_authenticated:
            return True

        if not hasattr(request, 'auth') or not request.auth:
            return True

        payload = getattr(request.auth, 'payload', None) or {}

        jwt_device = payload.get('device_id')
        if not jwt_device:
            return True  # Web user — no device binding

        # 1. Check revocation (fail-closed)
        try:
            if cache.get(f'revoked_device:{jwt_device}'):
                raise AuthenticationFailed('Device has been revoked.')
        except AuthenticationFailed:
            raise
        except Exception:
            raise AuthenticationFailed('Unable to verify device status.')

        # 2. Check device binding + grace period
        db_device = getattr(request.user, 'device_id', None)
        if jwt_device != db_device:
            try:
                grace_user_id = cache.get(f'grace_device:{jwt_device}')
            except Exception:
                raise AuthenticationFailed('Unable to verify device status.')
            if grace_user_id is None or str(grace_user_id) != str(request.user.id):
                raise AuthenticationFailed('Device not bound to this account.')

        return True
