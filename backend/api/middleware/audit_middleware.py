import json
import logging

from api.models import AuditLog

logger = logging.getLogger(__name__)


class AuditMiddleware:
    """
    Automatic CUD audit logging.

    Logs POST/PUT/PATCH/DELETE to AuditLog with user, action,
    table, record ID, and JSON payload (truncated at 10KB).
    Skips GET, auth endpoints, health check, and sync (has own logging).
    """

    SKIP_PREFIXES = (
        '/api/auth/',
        '/api/sync/',
    )
    SKIP_EXACT = ('/api/',)  # health check
    MAX_JSON_BYTES = 10_240  # 10KB

    METHOD_ACTION_MAP = {
        'POST': 'create',
        'PUT': 'update',
        'PATCH': 'update',
        'DELETE': 'delete',
    }

    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
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

    def _log(self, request, response):
        user = request.user if hasattr(request, 'user') and request.user.is_authenticated else None
        action = self.METHOD_ACTION_MAP.get(request.method, 'unknown')
        table_name = self._resolve_table(request.path)
        record_id = self._resolve_record_id(request, response)

        new_values = self._safe_json(request.data) if hasattr(request, 'data') else None
        old_values = getattr(request, '_audit_old_values', None)

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
        """Truncate JSON > 10KB to prevent log bloat."""
        try:
            raw = json.dumps(data, default=str)
        except (TypeError, ValueError):
            return {'_error': 'unserializable'}
        if len(raw) > self.MAX_JSON_BYTES:
            return {'_truncated': True, '_size': len(raw)}
        return data

    def _resolve_table(self, path):
        """Extract resource name from URL path (e.g., /api/trips/1/ → trips)."""
        parts = [p for p in path.strip('/').split('/') if p and p != 'api']
        return parts[0] if parts else 'unknown'

    def _resolve_record_id(self, request, response):
        """Extract record ID from URL or response."""
        parts = [p for p in request.path.strip('/').split('/') if p]
        for part in reversed(parts):
            if part.isdigit():
                return int(part)

        if hasattr(response, 'data') and isinstance(response.data, dict):
            return response.data.get('id', 0)

        return 0

    @staticmethod
    def _get_ip(request):
        forwarded = request.META.get('HTTP_X_FORWARDED_FOR')
        if forwarded:
            return forwarded.split(',')[0].strip()
        return request.META.get('REMOTE_ADDR')
