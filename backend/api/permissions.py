import logging

from django.core.cache import cache
from django.core.exceptions import ImproperlyConfigured
from rest_framework.exceptions import AuthenticationFailed
from rest_framework.permissions import BasePermission

logger = logging.getLogger(__name__)


# ---------- Single-source permission matrix ----------

PERMISSION_MATRIX = {
    'admin': [
        'view_all_offices', 'manage_users', 'manage_buses',
        'manage_pricing', 'view_audit_log', 'override_status',
        'approve_quarantine', 'revoke_devices', 'start_trip', 'complete_trip',
        'create_trip', 'view_office_trips', 'cancel_trip',
        'create_passenger_ticket', 'create_cargo_ticket',
        'receive_cargo', 'transition_cargo_status',
        'view_office_reports', 'export_excel', 'create_expense',
        'view_settlements', 'record_settlement', 'resolve_settlement',
    ],
    'office_staff': {
        'all': [
            'create_trip', 'view_office_trips', 'cancel_trip',
            'create_passenger_ticket', 'create_cargo_ticket',
            'receive_cargo', 'transition_cargo_status', 'view_office_reports', 'export_excel',
            'view_settlements', 'record_settlement',
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
        'create_expense', 'view_own_trip',
        'sync_batch',
    ],
    'driver': ['view_own_trip'],
}


def get_user_permissions(user):
    """Return flat permission list for a user based on role + department."""
    if user.role == 'admin':
        return PERMISSION_MATRIX['admin']
    if user.role == 'office_staff':
        dept = getattr(user, 'department', 'all') or 'all'
        return PERMISSION_MATRIX['office_staff'].get(dept, [])
    return PERMISSION_MATRIX.get(user.role, [])


def get_cached_user_permissions(request):
    """
    Returns user permissions, caching the result on the request object.
    Prevents redundant dictionary lookups during complex view lifecycles.
    """
    if not hasattr(request, '_permset'):
        if not request.user or not request.user.is_authenticated:
            request._permset = set()
        else:
            request._permset = set(get_user_permissions(request.user))
    return request._permset


# ---------- Permission classes ----------

class RBACPermission(BasePermission):
    """
    Checks request.user.role against view.required_roles.

    Usage on view:
        required_roles = ['admin', 'office_staff']
    """

    def has_permission(self, request, view):
        required_roles = getattr(view, 'required_roles', None) or getattr(self, 'required_roles', None)
        if required_roles is None:
            if not getattr(view, '_rbac_public', False):
                raise ImproperlyConfigured(
                    f"{view.__class__.__name__} uses RBACPermission without "
                    f"declaring required_roles. Add required_roles = [...] "
                    f"or _rbac_public = True for genuinely public views."
                )
            return True
        if not request.user or not request.user.is_authenticated:
            return False
        return request.user.role in required_roles


class IsAdminUser(BasePermission):
    """
    Enforces that the authenticated user has the 'admin' role.
    """

    def has_permission(self, request, view):
        return bool(request.user and request.user.is_authenticated and request.user.role == 'admin')


class MatrixPermission(BasePermission):
    """
    Checks if the user has specific action permissions based on the PERMISSION_MATRIX.
    This is the primary way to enforce authorization (vs scoping, which is OfficeScopePermission).

    Usage on view:
        required_actions = ['create_trip']              # Enforced globally
        required_actions = {'POST': ['create_trip']}    # Method-specific
        required_actions = {'start': ['start_trip']}    # Action-specific (ViewSets)
    """

    def has_permission(self, request, view):
        # Allow unrestricted public access if specified
        if getattr(view, '_rbac_public', False):
            return True

        if not request.user or not request.user.is_authenticated:
            return False

        # Determine what actions are required for this request
        required_mapping = getattr(view, 'required_actions', None)
        if not required_mapping:
            raise ImproperlyConfigured(
                f"{view.__class__.__name__} uses MatrixPermission without "
                f"declaring required_actions."
            )

        # required_actions can be a list or a dict
        if isinstance(required_mapping, list):
            required = required_mapping
        elif isinstance(required_mapping, dict):
            # Prefer ViewSet action over HTTP method
            action_name = getattr(view, 'action', None)
            method = request.method
            
            if action_name and action_name in required_mapping:
                required = required_mapping[action_name]
            elif method in required_mapping:
                required = required_mapping[method]
            elif 'default' in required_mapping:
                required = required_mapping['default']
            else:
                return False # Method/action not allowed by permissions
        else:
            raise ImproperlyConfigured("required_actions must be a list or dict.")

        if not required:
            # Empty list means no specific action required for this method
            return True

        user_perms = get_cached_user_permissions(request)
        
        # Must have ALL required actions
        return all(perm in user_perms for perm in required)

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
        if not request.user or not request.user.is_authenticated:
            return False
        if request.user.role != 'office_staff':
            return True  # Non-office roles not subject to dept filter
        user_dept = getattr(request.user, 'department', 'all') or 'all'
        return user_dept in ('all', required_dept)


class OfficeScopePermission(BasePermission):
    """
    Filters queryset to user's office. Admin bypasses.

    Works as object-level permission. Views should call
    `self.check_object_permissions(request, obj)`.
    """

    def has_object_permission(self, request, view, obj):
        user = request.user

        if not user or not user.is_authenticated:
            return False

        if user.role == 'admin':
            return True

        # Resolve the trip context when object is a trip itself or trip-owned model.
        trip_obj = obj if hasattr(obj, 'conductor_id') else getattr(obj, 'trip', None)

        # Conductors/drivers are scoped by assignment, not office.
        if user.role in ('conductor', 'driver'):
            conductor_id = getattr(trip_obj, 'conductor_id', None) if trip_obj is not None else None
            return conductor_id == user.id

        # Office staff scope by origin/destination office for trip-scoped records.
        if user.role == 'office_staff':
            office_id = getattr(user, 'office_id', None)
            if office_id is None:
                return False

            if trip_obj is not None:
                return (
                    getattr(trip_obj, 'origin_office_id', None) == office_id
                    or getattr(trip_obj, 'destination_office_id', None) == office_id
                )

            obj_office = getattr(obj, 'office', None) or getattr(obj, 'origin', None)
            if obj_office is None:
                return False
            return obj_office.id == office_id

        return False


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

        # 1. Check revocation (fail-closed gracefully with DB fallback)
        try:
            if cache.get(f'revoked_device:{jwt_device}'):
                raise AuthenticationFailed('Device has been revoked.')
        except AuthenticationFailed:
            raise
        except Exception as e:
            logger.error("Redis unavailable for device check, falling back to DB: %s", e)
            if getattr(request.user, 'device_revoked', False):
                raise AuthenticationFailed('Device has been revoked.')
            logger.warning("Device revocation check skipped for user %s due to Redis error", request.user.id)

        # 2. Check device binding + grace period
        db_device = getattr(request.user, 'device_id', None)
        if jwt_device != db_device:
            try:
                grace_user_id = cache.get(f'grace_device:{jwt_device}')
            except Exception as e:
                logger.error("Redis unavailable for grace check: %s", e)
                raise AuthenticationFailed('Unable to verify device status due to server error.')
            if grace_user_id is None or str(grace_user_id) != str(request.user.id):
                raise AuthenticationFailed('Device not bound to this account.')

        return True
