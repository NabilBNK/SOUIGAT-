import logging

from django.core.cache import cache
from rest_framework import status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from api.models import User
from api.permissions import DeviceBoundPermission

logger = logging.getLogger(__name__)

REVOCATION_TTL = 30 * 86400  # 30 days


def is_admin(request):
    return request.user.is_authenticated and request.user.role == 'admin'


@api_view(['POST'])
@permission_classes([IsAuthenticated, DeviceBoundPermission])
def revoke_device(request, pk):
    """
    POST /api/admin/users/{pk}/revoke-device/

    Admin-only. Bans the user's current device_id via Redis and clears it from DB.
    """
    if not is_admin(request):
        return Response({'error': 'Admin access required.'}, status=status.HTTP_403_FORBIDDEN)

    try:
        target_user = User.objects.get(pk=pk)
    except User.DoesNotExist:
        return Response({'error': 'User not found.'}, status=status.HTTP_404_NOT_FOUND)

    device_id = target_user.device_id
    if not device_id:
        return Response({'error': 'User has no bound device.'}, status=status.HTTP_400_BAD_REQUEST)

    cache.set(f'revoked_device:{device_id}', True, timeout=REVOCATION_TTL)

    target_user.device_id = None
    target_user.device_bound_at = None
    target_user.save(update_fields=['device_id', 'device_bound_at'])

    logger.warning(
        "Device revoked: admin=%s target_user=%s device=%s",
        request.user.id, pk, device_id,
    )

    return Response({'detail': f'Device {device_id} revoked.', 'device_id': device_id})


@api_view(['POST'])
@permission_classes([IsAuthenticated, DeviceBoundPermission])
def rescue_device(request, pk):
    """
    POST /api/admin/users/{pk}/rescue-device/

    Admin-only. Re-enables old device for 1 hour so conductor can sync orphaned data.
    Body: {"device_id": "old-device-id-to-rescue"}
    """
    if not is_admin(request):
        return Response({'error': 'Admin access required.'}, status=status.HTTP_403_FORBIDDEN)

    old_device_id = request.data.get('device_id')
    if not old_device_id:
        return Response({'error': 'device_id required.'}, status=status.HTTP_400_BAD_REQUEST)

    try:
        target_user = User.objects.get(pk=pk)
    except User.DoesNotExist:
        return Response({'error': 'User not found.'}, status=status.HTTP_404_NOT_FOUND)

    # Set 1 hour grace period (overwrite existing)
    timeout = 3600
    cache.set(f'grace_device:{old_device_id}', target_user.id, timeout=timeout)

    logger.info(
        "Device rescue: admin=%s target_user=%s device=%s timeout=%ss",
        request.user.id, pk, old_device_id, timeout,
    )

    return Response({
        'detail': f'Device {old_device_id} rescue active for {timeout}s.',
        'device_id': old_device_id,
        'grace_timeout': timeout,
    })
