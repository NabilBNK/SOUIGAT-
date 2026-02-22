import logging
from datetime import date

from django.core.cache import cache
from django.db.models.signals import post_save
from django.dispatch import receiver

from api.models import PricingConfig

logger = logging.getLogger(__name__)

PRICING_CACHE_PREFIX = 'pricing'


def _invalidate_pricing_cache(instance):
    """Delete cached pricing for all dates regarding the affected route."""
    try:
        from django_redis import get_redis_connection
        conn = get_redis_connection('default')
        pattern = f'*{PRICING_CACHE_PREFIX}:{instance.origin_office_id}:{instance.destination_office_id}:*'
        
        # NOTE: conn.keys() is O(N) total Redis keyspace. Safe at current scale (<2K keys).
        # UPGRADE PATH: Replace with conn.scan_iter(pattern) when Redis keyspace exceeds 100K.
        keys = conn.keys(pattern)
        if keys:
            deleted = conn.delete(*keys)
            logger.info('Pricing cache cleared %d keys: %s', deleted, pattern)
        else:
            logger.debug('Pricing cache: no keys to invalidate for %s', pattern)
    except ImportError:
        # Fallback for testing with LocMemCache
        # Try django-redis' pattern search alternative if connection fails
        try:
            cache.delete_pattern(f'*{PRICING_CACHE_PREFIX}:{instance.origin_office_id}:{instance.destination_office_id}:*')
        except AttributeError:
            # We are using DummyCache or LocMemCache without advanced operations
            cache.clear()
            logger.info('Cache cleared due to test setup lacking pattern matching in test environment.')


@receiver(post_save, sender=PricingConfig)
def invalidate_pricing_on_save(sender, instance, **kwargs):
    _invalidate_pricing_cache(instance)
