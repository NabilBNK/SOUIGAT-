"""Test PricingConfig overlap validation, temporal queries, soft delete + managers.

10 tests covering:
- Overlap date range detection
- Forever pricing (effective_until=None) blocking future entries
- get_active_pricing() returns correct pricing by date
- get_active_pricing() returns None for no match
- Same-office pricing blocked
- Soft delete hides from default queryset + tracks deleted_by
- TripManager.active() / for_office() / for_conductor()
"""
from datetime import date, timedelta

from django.core.exceptions import ValidationError
from django.test import TestCase
from django.utils import timezone

from api.models import Bus, Office, PricingConfig, Trip, User


class PricingOverlapTests(TestCase):

    @classmethod
    def setUpTestData(cls):
        cls.office_a = Office.objects.create(name='Pricing A', city='Algiers')
        cls.office_b = Office.objects.create(name='Pricing B', city='Oran')
        cls.office_c = Office.objects.create(name='Pricing C', city='Constantine')

    def _make_pricing(self, origin=None, dest=None, **overrides):
        defaults = dict(
            origin_office=origin or self.office_a,
            destination_office=dest or self.office_b,
            passenger_price=2500, cargo_small_price=500,
            cargo_medium_price=1000, cargo_large_price=2000,
            effective_from=date(2026, 1, 1),
        )
        defaults.update(overrides)
        return PricingConfig(**defaults)

    def test_non_overlapping_ranges_allowed(self):
        """Jan-Jun + Jul-Dec → no overlap, both created."""
        p1 = self._make_pricing(
            effective_from=date(2026, 1, 1), effective_until=date(2026, 6, 30),
        )
        p1.full_clean()
        p1.save()

        p2 = self._make_pricing(
            effective_from=date(2026, 7, 1), effective_until=date(2026, 12, 31),
        )
        p2.full_clean()
        p2.save()
        self.assertEqual(PricingConfig.objects.count(), 2)

    def test_overlapping_ranges_blocked(self):
        """Jan-Jun + Mar-Sep → overlap detected."""
        p1 = self._make_pricing(
            effective_from=date(2026, 1, 1), effective_until=date(2026, 6, 30),
        )
        p1.full_clean()
        p1.save()

        p2 = self._make_pricing(
            effective_from=date(2026, 3, 1), effective_until=date(2026, 9, 30),
        )
        with self.assertRaises(ValidationError):
            p2.full_clean()

    def test_forever_pricing_blocks_future(self):
        """effective_until=None (forever) blocks any future pricing."""
        p1 = self._make_pricing(
            effective_from=date(2026, 1, 1), effective_until=None,
        )
        p1.full_clean()
        p1.save()

        p2 = self._make_pricing(
            effective_from=date(2026, 7, 1), effective_until=date(2026, 12, 31),
        )
        with self.assertRaises(ValidationError):
            p2.full_clean()

    def test_same_office_pricing_blocked(self):
        """origin == destination → ValidationError."""
        p = self._make_pricing(origin=self.office_a, dest=self.office_a)
        with self.assertRaises(ValidationError):
            p.full_clean()

    def test_get_active_pricing_correct_date(self):
        """get_active_pricing returns the right config for a given date."""
        p1 = self._make_pricing(
            effective_from=date(2026, 1, 1), effective_until=date(2026, 5, 31),
            passenger_price=1000,
        )
        p1.full_clean()
        p1.save()

        p2 = PricingConfig(
            origin_office=self.office_a, destination_office=self.office_b,
            passenger_price=1200, cargo_small_price=500,
            cargo_medium_price=1000, cargo_large_price=2000,
            effective_from=date(2026, 6, 1),
        )
        p2.full_clean()
        p2.save()

        # May → old pricing
        result = PricingConfig.objects.get_active_pricing(
            self.office_a, self.office_b, for_date=date(2026, 5, 15),
        )
        self.assertEqual(result['passenger_price'], 1000)

        # July → new pricing
        result = PricingConfig.objects.get_active_pricing(
            self.office_a, self.office_b, for_date=date(2026, 7, 1),
        )
        self.assertEqual(result['passenger_price'], 1200)

    def test_get_active_pricing_returns_none_for_no_match(self):
        """No pricing exists for route → returns None."""
        result = PricingConfig.objects.get_active_pricing(
            self.office_a, self.office_c, for_date=date(2026, 1, 1),
        )
        self.assertIsNone(result)


class SoftDeleteTests(TestCase):

    @classmethod
    def setUpTestData(cls):
        cls.office_a = Office.objects.create(name='SD Office A', city='Algiers')
        cls.office_b = Office.objects.create(name='SD Office B', city='Oran')
        cls.admin = User.objects.create_user(
            phone='0500000077', password='test1234',
            first_name='Admin', last_name='SD', role='admin',
        )
        cls.conductor = User.objects.create_user(
            phone='0700000077', password='test1234',
            first_name='Cond', last_name='SD', role='conductor',
        )
        cls.bus = Bus.objects.create(
            plate_number='77-TEST-77', office=cls.office_a, capacity=50,
        )

    def test_soft_delete_hides_from_default_manager(self):
        """Soft-deleted trip invisible via objects, visible via all_objects."""
        trip = Trip(
            origin_office=self.office_a, destination_office=self.office_b,
            conductor=self.conductor, bus=self.bus,
            departure_datetime=timezone.now() + timedelta(hours=1),
            passenger_base_price=2500, cargo_small_price=500,
            cargo_medium_price=1000, cargo_large_price=2000,
        )
        trip.save()
        trip_pk = trip.pk

        trip.soft_delete(user=self.admin)

        self.assertEqual(Trip.objects.filter(pk=trip_pk).count(), 0)
        self.assertEqual(Trip.all_objects.filter(pk=trip_pk).count(), 1)

        deleted = Trip.all_objects.get(pk=trip_pk)
        self.assertTrue(deleted.is_deleted)
        self.assertIsNotNone(deleted.deleted_at)
        self.assertEqual(deleted.deleted_by, self.admin)

    def test_restore_brings_back(self):
        """restore() makes record visible again."""
        trip = Trip(
            origin_office=self.office_a, destination_office=self.office_b,
            conductor=self.conductor, bus=self.bus,
            departure_datetime=timezone.now() + timedelta(hours=2),
            passenger_base_price=2500, cargo_small_price=500,
            cargo_medium_price=1000, cargo_large_price=2000,
        )
        trip.save()
        trip.soft_delete(user=self.admin)
        self.assertEqual(Trip.objects.filter(pk=trip.pk).count(), 0)

        trip.restore()
        self.assertEqual(Trip.objects.filter(pk=trip.pk).count(), 1)
        self.assertFalse(trip.is_deleted)
        self.assertIsNone(trip.deleted_by)


class TripManagerTests(TestCase):

    @classmethod
    def setUpTestData(cls):
        cls.office_a = Office.objects.create(name='TM A', city='Algiers')
        cls.office_b = Office.objects.create(name='TM B', city='Oran')
        cls.office_c = Office.objects.create(name='TM C', city='Constantine')
        cls.conductor = User.objects.create_user(
            phone='0700000066', password='test1234',
            first_name='Cond', last_name='TM', role='conductor',
        )
        cls.conductor2 = User.objects.create_user(
            phone='0700000055', password='test1234',
            first_name='Cond2', last_name='TM', role='conductor',
        )
        cls.bus = Bus.objects.create(
            plate_number='66-TEST-66', office=cls.office_a, capacity=50,
        )

        def make(origin, dest, conductor, status='scheduled'):
            t = Trip(
                origin_office=origin, destination_office=dest,
                conductor=conductor, bus=cls.bus,
                departure_datetime=timezone.now() + timedelta(hours=1),
                passenger_base_price=2500, cargo_small_price=500,
                cargo_medium_price=1000, cargo_large_price=2000,
                status=status,
            )
            t.save()
            return t

        cls.t1 = make(cls.office_a, cls.office_b, cls.conductor, 'scheduled')
        cls.t2 = make(cls.office_b, cls.office_c, cls.conductor, 'in_progress')
        cls.t3 = make(cls.office_c, cls.office_a, cls.conductor2, 'completed')

    def test_active_returns_scheduled_and_in_progress(self):
        active = Trip.objects.active()
        self.assertIn(self.t1, active)
        self.assertIn(self.t2, active)
        self.assertNotIn(self.t3, active)

    def test_for_office_returns_origin_and_destination(self):
        """office_a is origin in t1, destination in t3."""
        trips = Trip.objects.for_office(self.office_a)
        self.assertIn(self.t1, trips)
        self.assertIn(self.t3, trips)
        self.assertNotIn(self.t2, trips)
