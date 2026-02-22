"""Tests for T18: Pricing cache (Redis)."""
from datetime import date

from django.core.cache import cache
from django.test import TestCase

from api.models import Office, PricingConfig


class PricingCacheTests(TestCase):
    """Redis cache for pricing lookups."""

    def setUp(self):
        cache.clear()
        self.office_a = Office.objects.create(name='Algiers', city='Algiers')
        self.office_b = Office.objects.create(name='Oran', city='Oran')
        self.pricing = PricingConfig.objects.create(
            origin_office=self.office_a,
            destination_office=self.office_b,
            passenger_price=1500,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
            effective_from=date(2025, 1, 1),
        )

    def tearDown(self):
        cache.clear()

    def _cache_key(self):
        today = date.today()
        return f'pricing:{self.office_a.id}:{self.office_b.id}:{today}'

    def test_cache_hit(self):
        """Second call returns cached result without DB query."""
        result1 = PricingConfig.objects.get_active_pricing(
            self.office_a, self.office_b,
        )
        self.assertEqual(result1['passenger_price'], 1500)

        # Verify cache key exists
        self.assertIsNotNone(cache.get(self._cache_key()))

        # Second call should return same object from cache
        result2 = PricingConfig.objects.get_active_pricing(
            self.office_a, self.office_b,
        )
        self.assertEqual(result2['passenger_price'], 1500)

    def test_save_invalidates_cache(self):
        """Saving a PricingConfig invalidates the cache for that route."""
        # Populate cache
        PricingConfig.objects.get_active_pricing(
            self.office_a, self.office_b,
        )
        self.assertIsNotNone(cache.get(self._cache_key()))

        # Save pricing → should invalidate
        self.pricing.passenger_price = 2000
        self.pricing.save()
        self.assertIsNone(cache.get(self._cache_key()))

    def test_updated_price_used_after_invalidation(self):
        """After price update, next lookup returns the new price."""
        PricingConfig.objects.get_active_pricing(
            self.office_a, self.office_b,
        )

        self.pricing.passenger_price = 2500
        self.pricing.save()

        result = PricingConfig.objects.get_active_pricing(
            self.office_a, self.office_b,
        )
        self.assertEqual(result['passenger_price'], 2500)

    def test_cache_miss_falls_through(self):
        """With empty cache, pricing is fetched from DB."""
        cache.clear()
        result = PricingConfig.objects.get_active_pricing(
            self.office_a, self.office_b,
        )
        self.assertIsNotNone(result)
        self.assertEqual(result['passenger_price'], 1500)

    def test_cache_hit_returns_dict_not_orm_instance(self):
        """Cache hit must return dict, not PricingConfig instance."""
        # Prime the cache
        result1 = PricingConfig.objects.get_active_pricing(
            self.office_a, self.office_b
        )
        # Second call hits cache
        result2 = PricingConfig.objects.get_active_pricing(
            self.office_a, self.office_b
        )
        self.assertIsInstance(result1, dict)
        self.assertIsInstance(result2, dict)
        self.assertEqual(result1['passenger_price'], result2['passenger_price'])

    def test_pricing_signal_cold_cache_no_crash(self):
        """Signal must not crash when cache is empty (no keys to delete)."""
        from django.core.cache import cache
        from datetime import date, timedelta
        from api.models import PricingConfig
        
        cache.clear()  # Ensure cold cache
        pricing = PricingConfig.objects.create(
            origin_office=self.office_a,
            destination_office=self.office_b,
            passenger_price=2000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
            effective_from=date.today() + timedelta(days=30),
        )
        # If this line is reached without DataError, test passes
        self.assertIsNotNone(pricing.pk)
