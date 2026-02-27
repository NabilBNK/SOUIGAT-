import logging
import threading
from contextlib import contextmanager
from datetime import date

from django.core.cache import cache
from django.db.models.signals import post_save, post_delete
from django.dispatch import receiver

from api.models import PricingConfig

logger = logging.getLogger(__name__)

PRICING_CACHE_PREFIX = 'pricing'
REPORT_CACHE_PREFIX = 'report:daily'

# Thread-local flag to suppress signals during sync batch processing
_signal_state = threading.local()


@contextmanager
def suppress_report_signals():
    """Suppress per-ticket cache invalidation during batch operations.
    
    Uses try/finally to guarantee the flag is always cleared,
    even if an exception occurs mid-batch.
    """
    _signal_state.suppress_report = True
    try:
        yield
    finally:
        _signal_state.suppress_report = False


def _is_report_signal_suppressed():
    return getattr(_signal_state, 'suppress_report', False)


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


def _invalidate_report_cache(trip):
    """Invalidate report cache keys for the trip's departure date."""
    if not trip or not hasattr(trip, 'departure_datetime') or not trip.departure_datetime:
        return
    try:
        dep_date = trip.departure_datetime.date().isoformat()
        # Clear all report keys that match this date
        from django_redis import get_redis_connection
        conn = get_redis_connection('default')
        pattern = f'*{REPORT_CACHE_PREFIX}:*:{dep_date}*'
        keys = conn.keys(pattern)
        if keys:
            deleted = conn.delete(*keys)
            logger.info('Report cache cleared %d keys for date %s', deleted, dep_date)
    except ImportError:
        # Fallback: clear all cache in test environments
        try:
            cache.delete_pattern(f'*{REPORT_CACHE_PREFIX}:*')
        except AttributeError:
            cache.clear()
    except Exception:
        logger.debug('Report cache invalidation skipped', exc_info=True)


@receiver(post_save, sender=PricingConfig)
def invalidate_pricing_on_save(sender, instance, **kwargs):
    _invalidate_pricing_cache(instance)


def _on_ticket_or_expense_change(sender, instance, **kwargs):
    """Invalidate report cache when ticket or expense data changes.
    
    Suppressed during sync batch processing to avoid 50x Redis SCAN.
    Sync views call invalidate_report_cache() once after the batch completes.
    """
    if _is_report_signal_suppressed():
        return
    trip = getattr(instance, 'trip', None)
    _invalidate_report_cache(trip)


# Lazy import to avoid circular imports at module load time
def _connect_report_signals():
    """Connect report cache invalidation signals for ticket and expense models."""
    from api.models import PassengerTicket, CargoTicket, TripExpense

    for model in (PassengerTicket, CargoTicket, TripExpense):
        post_save.connect(_on_ticket_or_expense_change, sender=model)
        post_delete.connect(_on_ticket_or_expense_change, sender=model)


# Connect on import
_connect_report_signals()
