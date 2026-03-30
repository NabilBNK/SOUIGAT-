import hashlib
import hmac
import os
import uuid

from django.conf import settings
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework.exceptions import PermissionDenied

from api.tasks import generate_excel_report
from api.permissions import get_cached_user_permissions


EXPORT_ROLES = ('admin',)


def _check_export_perm(request, required_perm='export_excel'):
    """Exports are admin-only and still require the export matrix permission."""
    if not request.user.is_authenticated:
        raise PermissionDenied('Authentication required.')

    if request.user.role != 'admin':
        raise PermissionDenied('Exports are admin-only.')

    perms = get_cached_user_permissions(request)
    if required_perm not in perms:
        raise PermissionDenied('Insufficient permissions for exports.')


def _sign_download(task_id, user_id):
    """Generate HMAC token for download URL (1h expiry baked into task result)."""
    key = settings.SECRET_KEY.encode()
    msg = f'{task_id}:{user_id}'.encode()
    return hmac.HMAC(key, msg, hashlib.sha256).hexdigest()


def _verify_download(task_id, user_id, token):
    """Verify HMAC token."""
    expected = _sign_download(task_id, user_id)
    return hmac.compare_digest(expected, token)


@api_view(['POST'])
def trigger_export(request):
    """
    Trigger async Excel export.

    Body: {"report_type": "daily|trip|route", "filters": {...}}
    Returns: {"task_id": "...", "download_token": "..."}
    """
    _check_export_perm(request)

    report_type = request.data.get('report_type', 'daily')
    if report_type not in ('daily', 'trip', 'route'):
        return Response(
            {'detail': 'Invalid report_type. Use: daily, trip, route'},
            status=status.HTTP_400_BAD_REQUEST,
        )

    filters = request.data.get('filters', {})

    result = generate_excel_report.delay(
        report_type=report_type,
        filters=filters,
        user_id=request.user.id,
    )

    download_token = _sign_download(result.id, request.user.id)

    return Response({
        'task_id': result.id,
        'download_token': download_token,
        'status': 'pending',
    }, status=status.HTTP_202_ACCEPTED)


@api_view(['GET'])
def export_status(request, task_id):
    """Poll export task status."""
    _check_export_perm(request)

    from celery.result import AsyncResult
    result = AsyncResult(task_id)

    data = {
        'task_id': task_id,
        'status': result.status.lower(),
    }
    if result.ready() and result.successful():
        data['filename'] = result.result.get('filename', '')
    elif result.failed():
        data['error'] = str(result.result)

    return Response(data)


@api_view(['GET'])
def export_download(request, task_id):
    """
    Download completed export.

    Query param: ?token=<hmac_token>
    Streams .xlsx file.
    """
    token = request.query_params.get('token', '')
    if not _verify_download(task_id, request.user.id, token):
        return Response(
            {'detail': 'Invalid or expired download token.'},
            status=status.HTTP_403_FORBIDDEN,
        )

    from celery.result import AsyncResult
    result = AsyncResult(task_id)

    if not result.ready() or not result.successful():
        return Response(
            {'detail': 'Export not ready.'},
            status=status.HTTP_404_NOT_FOUND,
        )

    filename = result.result.get('filename', '')
    filepath = os.path.join(settings.MEDIA_ROOT, 'exports', filename)

    if not os.path.exists(filepath):
        return Response(
            {'detail': 'Export file not found.'},
            status=status.HTTP_404_NOT_FOUND,
        )

    from django.http import FileResponse
    return FileResponse(
        open(filepath, 'rb'),
        as_attachment=True,
        filename=filename,
        content_type='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    )
