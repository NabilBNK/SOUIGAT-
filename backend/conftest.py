import django
from django.conf import settings


def pytest_configure(config):
    """Ensure Django settings are configured before any test module is imported."""
    import os
    os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'souigat.settings')
