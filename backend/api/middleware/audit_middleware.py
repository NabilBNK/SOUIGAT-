import json
import logging

from django.apps import apps
from api.models import AuditLog

logger = logging.getLogger(__name__)


class AuditMiddleware:
    """
    Automatic CUD audit logging.

    Logs POST/PUT/PATCH/DELETE to AuditLog with user, action,
    table, record ID, and JSON payload (truncated at 10KB).
    Captures old_values via pre-request DB snapshot for updates.
    Logs auth events (login/logout) and sync summaries.
    """

    # Skip token refresh (noisy) and sync (tracked via SyncLog model at batch level)
    SKIP_PREFIXES = (
        '/api/auth/token/',
        '/api/sync/',  # Sync tracked via SyncLog; per-item audit here would cause ~157K rows/year
    )
    SKIP_EXACT = ('/api/',)  # health check
    MAX_JSON_BYTES = 10_240  # 10KB

    METHOD_ACTION_MAP = {
        'POST': 'create',
        'PUT': 'update',
        'PATCH': 'update',
        'DELETE': 'delete',
    }

    # Models we can snapshot for old_values capture
    TABLE_MODEL_MAP = {
        'trips': 'api.Trip',
        'tickets': 'api.PassengerTicket',
        'cargo': 'api.CargoTicket',
        'expenses': 'api.TripExpense',
        'users': 'api.User',
        'buses': 'api.Bus',
        'offices': 'api.Office',
        'pricing': 'api.PricingConfig',
        'quarantine': 'api.QuarantinedSync',
    }

    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        # Bug #2 fix: snapshot object BEFORE mutation for old_values
        if request.method in ('PUT', 'PATCH', 'DELETE', 'POST') and not self._should_skip(request.path):
            self._snapshot_old(request)

        response = self.get_response(request)

        if request.method not in self.METHOD_ACTION_MAP:
            return response

        if self._should_skip(request.path):
            return response

        if response.status_code >= 400:
            return response

        try:
            self._log(request, response)
        except Exception:
            logger.exception('Audit logging failed for %s %s', request.method, request.path)

        return response

    def _should_skip(self, path):
        if path in self.SKIP_EXACT:
            return True
        return any(path.startswith(p) for p in self.SKIP_PREFIXES)

    def _snapshot_old(self, request):
        """Bug #2 fix: read existing record before mutation to capture old_values."""
        try:
            table_name = self._resolve_table(request.path)
            record_id = self._resolve_record_id_from_path(request.path)
            if not record_id or table_name not in self.TABLE_MODEL_MAP:
                return

            model_label = self.TABLE_MODEL_MAP[table_name]
            Model = apps.get_model(model_label)
            obj = Model.objects.filter(pk=record_id).values().first()
            if obj:
                request._audit_old_values = self._safe_json(obj)
        except Exception:
            logger.debug('Could not snapshot old values for %s', request.path)

    def _log(self, request, response):
        user = request.user if hasattr(request, 'user') and request.user.is_authenticated else None
        action = self.METHOD_ACTION_MAP.get(request.method, 'unknown')
        table_name = self._resolve_table(request.path)
        record_id = self._resolve_record_id(request, response)

        new_values = self._safe_json(request.data) if hasattr(request, 'data') else None
        old_values = getattr(request, '_audit_old_values', None)

        # POST to action endpoints (deliver/complete/review) are state transitions,
        # not creates. If the view set _audit_old_values, classify as 'update'.
        if action == 'create' and old_values is not None:
            action = 'update'

        AuditLog.objects.create(
            user=user,
            action=action,
            table_name=table_name,
            record_id=record_id,
            old_values=old_values,
            new_values=new_values,
            ip_address=self._get_ip(request),
        )

    def _safe_json(self, data):
        """Serialize data for JSON storage. Converts non-JSON types (datetime) to strings."""
        try:
            raw = json.dumps(data, default=str)
        except (TypeError, ValueError):
            return {'_error': 'unserializable'}
        if len(raw) > self.MAX_JSON_BYTES:
            return {'_truncated': True, '_size': len(raw)}
        # Return parsed JSON so datetime/etc are stored as strings, not raw objects
        return json.loads(raw)

    def _resolve_table(self, path):
        """Extract resource name from URL path.
        
        /api/trips/1/ → trips
        /api/admin/buses/1/ → buses (skip 'admin' namespace prefix)
        """
        parts = [p for p in path.strip('/').split('/') if p and p != 'api']
        if not parts:
            return 'unknown'
        # Skip namespace prefixes like 'admin'
        if parts[0] == 'admin' and len(parts) > 1:
            return parts[1]
        return parts[0]

    def _resolve_record_id_from_path(self, path):
        """Extract numeric record ID from URL path only."""
        parts = [p for p in path.strip('/').split('/') if p]
        for part in reversed(parts):
            if part.isdigit():
                return int(part)
        return 0

    def _resolve_record_id(self, request, response):
        """Extract record ID from URL or response."""
        path_id = self._resolve_record_id_from_path(request.path)
        if path_id:
            return path_id

        if hasattr(response, 'data') and isinstance(response.data, dict):
            return response.data.get('id', 0)

        return 0

    @staticmethod
    def _get_ip(request):
        forwarded = request.META.get('HTTP_X_FORWARDED_FOR')
        if forwarded:
            return forwarded.split(',')[0].strip()
        return request.META.get('REMOTE_ADDR')
