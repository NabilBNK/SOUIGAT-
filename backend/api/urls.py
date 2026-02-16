from django.urls import path
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.response import Response

from api.views import admin_views, auth


@api_view(['GET'])
@permission_classes([AllowAny])
def health_check(request):
    return Response({'status': 'ok', 'service': 'souigat-api'})


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
]
