import logging

from django.core.cache import cache
from django.db import transaction
from django.utils import timezone
from rest_framework import status
from rest_framework.decorators import api_view, permission_classes, throttle_classes
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.throttling import AnonRateThrottle
from rest_framework_simplejwt.exceptions import InvalidToken, TokenError
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework_simplejwt.views import TokenRefreshView

from api.models import User
from api.serializers.auth import LoginSerializer, UserProfileSerializer
from api.tokens import DeviceBoundRefreshToken

logger = logging.getLogger(__name__)


class LoginThrottle(AnonRateThrottle):
    rate = '5/minute'

    def get_cache_key(self, request, view):
        phone = request.data.get('phone')
        if not phone:
            return None
        return f'login_throttle:{phone}'


@api_view(['POST'])
@permission_classes([AllowAny])
@throttle_classes([LoginThrottle])
def login(request):
    """
    POST /api/auth/login/

    Authenticates user, binds device atomically, returns tokens.
    Device switch sets 30-min grace period for old device to sync.
    """
    serializer = LoginSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    user = serializer.validated_data['user']
    device_id = serializer.validated_data.get('device_id')
    platform = serializer.validated_data.get('platform', 'web')

    with transaction.atomic():
        user = User.objects.select_for_update().get(pk=user.pk)
        old_device_id = user.device_id

        user.last_login = timezone.now()
        update_fields = ['last_login']

        if device_id:
            user.device_id = device_id
            user.device_bound_at = timezone.now()
            update_fields += ['device_id', 'device_bound_at']

        user.save(update_fields=update_fields)

        # Grace period for old device (30 min to sync orphaned data)
        if old_device_id and old_device_id != device_id:
            cache.set(f'grace_device:{old_device_id}', user.id, timeout=1800)
            logger.warning(
                "Device switch: user=%s old=%s new=%s",
                user.id, old_device_id, device_id,
            )

    refresh = DeviceBoundRefreshToken.for_user_and_device(
        user=user,
        device_id=device_id,
        platform=platform,
    )

    return Response({
        'access': str(refresh.access_token),
        'refresh': str(refresh),
        'user': UserProfileSerializer(user).data,
        'device_bound': bool(device_id),
        'token_strategy': 'persistent' if platform == 'mobile' else 'rotating',
    })


class PlatformAwareTokenRefreshView(TokenRefreshView):
    """
    POST /api/auth/token/refresh/

    Web: blacklists old token, returns new refresh + access (rotating).
    Mobile: returns new access only, same refresh stays valid (persistent).
    """
    permission_classes = [AllowAny]

    def post(self, request, *args, **kwargs):
        refresh_token_str = request.data.get('refresh')
        device_id = request.data.get('device_id')

        if not refresh_token_str:
            return Response(
                {'error': 'refresh token required'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        try:
            token = DeviceBoundRefreshToken(refresh_token_str)

            if device_id:
                token.verify_device(device_id)

            platform = token.payload.get('platform', 'web')

            if platform == 'web':
                # Rotate: blacklist old, issue new pair
                token.blacklist()

                user = User.objects.get(id=token['user_id'])
                new_refresh = DeviceBoundRefreshToken.for_user_and_device(
                    user=user,
                    device_id=device_id,
                    platform='web',
                )

                return Response({
                    'access': str(new_refresh.access_token),
                    'refresh': str(new_refresh),
                    'strategy': 'rotated',
                })
            else:
                # Persistent: new access from existing refresh
                access = token.access_token
                return Response({
                    'access': str(access),
                    'strategy': 'persistent',
                })

        except (TokenError, InvalidToken) as e:
            return Response(
                {'error': str(e)},
                status=status.HTTP_401_UNAUTHORIZED,
            )


@api_view(['GET'])
def me(request):
    """GET /api/auth/me/ — Returns user profile with permissions."""
    return Response(UserProfileSerializer(request.user).data)


@api_view(['POST'])
def logout(request):
    """POST /api/auth/logout/ — Blacklists the refresh token."""
    refresh_token_str = request.data.get('refresh')
    if not refresh_token_str:
        return Response(
            {'error': 'refresh token required'},
            status=status.HTTP_400_BAD_REQUEST,
        )

    try:
        token = RefreshToken(refresh_token_str)
        token.blacklist()
        return Response({'detail': 'Logout successful.'})
    except (TokenError, InvalidToken) as e:
        return Response({'error': str(e)}, status=status.HTTP_400_BAD_REQUEST)
