import hashlib

from django.conf import settings
from django.contrib import admin
from django.urls import include, path

from decouple import config

# Randomize admin URL. Uses DJANGO_ADMIN_PATH env var if set,
# otherwise derives a 12-char hex slug from ADMIN_SECRET so it's
# unique per deployment but completely decoupled from SECRET_KEY.
# IMPORTANT: Rotating ADMIN_SECRET will change this URL. Notify admins before rotating.
_admin_secret = config('ADMIN_SECRET', default='dev-admin-secret-replace-in-production')
_admin_path = config(
    'DJANGO_ADMIN_PATH',
    default=hashlib.sha256(_admin_secret.encode()).hexdigest()[:12],
) + '/'

urlpatterns = [
    path(_admin_path, admin.site.urls),
    path('api/', include('api.urls')),
]
