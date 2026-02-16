import logging
from datetime import timedelta

from celery import shared_task
from django.utils import timezone

logger = logging.getLogger(__name__)


@shared_task
def cleanup_expired_tokens():
    """Delete expired outstanding tokens. Runs daily via Celery Beat."""
    from rest_framework_simplejwt.token_blacklist.models import OutstandingToken

    cutoff = timezone.now() - timedelta(days=30)
    deleted, _ = OutstandingToken.objects.filter(expires_at__lt=cutoff).delete()
    if deleted:
        logger.info("Cleaned up %d expired tokens", deleted)
