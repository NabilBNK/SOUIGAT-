"""Comprehensive tests for all remaining untested bug fixes.

Covers: cargo deliver consolidation (Bug #8), expense access (Bug #14),
cache invalidation signals, report cache key fix.
"""
from datetime import date, timedelta

from django.core.cache import cache
from django.test import TestCase, override_settings
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import (
    Bus, CargoTicket, Office, PassengerTicket,
    PricingConfig, Trip, TripExpense, User,
)


class CargoDeliverConsolidationTests(TestCase):
    """Bug #8: Deliver action should set all fields in a single save."""

    def setUp(self):
        self.office = Office.objects.create(name='HQ', city='Algiers')
        self.office_b = Office.objects.create(name='Branch', city='Oran')
        self.conductor = User.objects.create_user(
            phone='0550000001', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office,
        )
        self.staff = User.objects.create_user(
            phone='0550000002', password='pass',
            first_name='Staff', last_name='A',
            role='office_staff', office=self.office_b,
        )
        bus = Bus.objects.create(
            plate_number='BUS-01', capacity=50, office=self.office,
        )
        PricingConfig.objects.create(
            origin_office=self.office,
            destination_office=self.office_b,
            passenger_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
            effective_from=date.today(),
        )
        self.trip = Trip.objects.create(
            origin_office=self.office,
            destination_office=self.office_b,
            conductor=self.conductor, bus=bus,
            departure_datetime=timezone.now(),
            status='in_progress',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )
        self.cargo = CargoTicket.objects.create(
            trip=self.trip, ticket_number='CT-1-001',
            sender_name='S', receiver_name='R',
            cargo_tier='small', price=500,
            payment_source='cash', created_by=self.conductor,
            status='arrived',  # arrived→delivered is valid per FSM
        )
        self.client = APIClient()

    def test_deliver_sets_all_fields_atomically(self):
        """Deliver action sets status, delivered_by, delivered_at, version in one save."""
        self.client.force_authenticate(self.staff)
        resp = self.client.post(f'/api/cargo/{self.cargo.id}/deliver/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)

        self.cargo.refresh_from_db()
        self.assertEqual(self.cargo.status, 'delivered')
        self.assertIsNotNone(self.cargo.delivered_by)
        self.assertIsNotNone(self.cargo.delivered_at)
        self.assertEqual(self.cargo.version, 2)

    def test_deliver_from_created_rejected(self):
        """Cannot deliver cargo from 'in_transit' status (must be 'arrived' first)."""
        self.cargo.status = 'in_transit'
        self.cargo.save(skip_transition_check=True)
        self.client.force_authenticate(self.staff)
        resp = self.client.post(f'/api/cargo/{self.cargo.id}/deliver/')
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)


class ExpenseAccessTests(TestCase):
    """Bug #14: Admin and office_staff should be able to view expenses."""

    def setUp(self):
        self.office = Office.objects.create(name='HQ', city='Algiers')
        self.office_b = Office.objects.create(name='Branch', city='Oran')
        self.admin = User.objects.create_superuser(
            phone='0550000001', password='pass',
            first_name='Admin', last_name='X',
        )
        self.conductor = User.objects.create_user(
            phone='0550000003', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office,
        )
        bus = Bus.objects.create(
            plate_number='BUS-01', capacity=50, office=self.office,
        )
        self.trip = Trip.objects.create(
            origin_office=self.office,
            destination_office=self.office_b,
            conductor=self.conductor, bus=bus,
            departure_datetime=timezone.now(),
            status='in_progress',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )
        TripExpense.objects.create(
            trip=self.trip, description='Fuel',
            amount=300, created_by=self.conductor,
        )
        self.client = APIClient()

    def test_admin_can_list_expenses(self):
        """Admin can view all expenses."""
        self.client.force_authenticate(self.admin)
        resp = self.client.get('/api/expenses/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)

    def test_conductor_can_list_own_expenses(self):
        """Conductor can view their own expenses."""
        self.client.force_authenticate(self.conductor)
        resp = self.client.get('/api/expenses/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)


@override_settings(CACHES={'default': {'BACKEND': 'django.core.cache.backends.locmem.LocMemCache'}})
class ReportCacheTests(TestCase):
    """Tests for report cache key fix and invalidation signals."""

    def setUp(self):
        self.office = Office.objects.create(name='HQ', city='Algiers')
        self.office_b = Office.objects.create(name='Branch', city='Oran')
        self.admin = User.objects.create_superuser(
            phone='0550000001', password='pass',
            first_name='Admin', last_name='X',
        )
        self.conductor = User.objects.create_user(
            phone='0550000003', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office,
        )
        bus = Bus.objects.create(
            plate_number='BUS-01', capacity=50, office=self.office,
        )
        self.trip = Trip.objects.create(
            origin_office=self.office,
            destination_office=self.office_b,
            conductor=self.conductor, bus=bus,
            departure_datetime=timezone.now(),
            status='in_progress',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )
        self.client = APIClient()

    def test_cache_key_uses_office_not_role(self):
        """Cache key should use office_id, not role."""
        self.client.force_authenticate(self.admin)
        today = timezone.now().strftime('%Y-%m-%d')
        # First request populates cache
        resp1 = self.client.get(f'/api/reports/daily/?date_from={today}&date_to={today}')
        self.assertEqual(resp1.status_code, status.HTTP_200_OK)

        # Verify cache key format uses 'all' for admin
        expected_key = f'report:daily:all:{today}:{today}'
        cached = cache.get(expected_key)
        self.assertIsNotNone(cached, f'Expected cache key "{expected_key}" to be populated')

    def test_ticket_creation_triggers_cache_clear(self):
        """Creating a ticket should trigger report cache invalidation."""
        self.client.force_authenticate(self.admin)
        today = timezone.now().strftime('%Y-%m-%d')

        # Populate cache
        self.client.get(f'/api/reports/daily/?date_from={today}&date_to={today}')

        # Create a ticket (triggers signal)
        PassengerTicket.objects.create(
            trip=self.trip, ticket_number='PT-test-001',
            passenger_name='Test', price=1000,
            created_by=self.conductor,
        )

        # Cache should have been cleared by signal (at least in LocMemCache fallback)
        # The signal handler calls cache.clear() in LocMemCache environments
        # So all keys should be gone
        expected_key = f'report:daily:all:{today}:{today}'
        cached = cache.get(expected_key)
        self.assertIsNone(cached, 'Cache should be invalidated after ticket creation')


class TicketNumberSerializationTests(TestCase):
    """Bug #6 (corrected): select_for_update prevents race, no retry needed."""

    def setUp(self):
        self.office = Office.objects.create(name='HQ', city='Algiers')
        self.office_b = Office.objects.create(name='Branch', city='Oran')
        self.conductor = User.objects.create_user(
            phone='0550000001', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office,
        )
        bus = Bus.objects.create(
            plate_number='BUS-01', capacity=50, office=self.office,
        )
        PricingConfig.objects.create(
            origin_office=self.office,
            destination_office=self.office_b,
            passenger_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
            effective_from=date.today(),
        )
        self.trip = Trip.objects.create(
            origin_office=self.office,
            destination_office=self.office_b,
            conductor=self.conductor, bus=bus,
            departure_datetime=timezone.now() + timedelta(hours=1),
            status='in_progress',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )
        self.client = APIClient()

    def test_sequential_ticket_numbers_unique(self):
        """Multiple ticket creations produce unique sequential numbers."""
        self.client.force_authenticate(self.conductor)
        numbers = []
        for _ in range(5):
            resp = self.client.post('/api/tickets/', {
                'trip': self.trip.id,
                'passenger_name': 'Test',
                'payment_source': 'cash',
            }, format='json')
            self.assertEqual(resp.status_code, status.HTTP_201_CREATED)
            numbers.append(resp.data['ticket_number'])

        # All ticket numbers should be unique
        self.assertEqual(len(numbers), len(set(numbers)), f'Duplicate numbers: {numbers}')

        # Numbers should be sequential
        self.assertEqual(numbers, [
            f'PT-{self.trip.id}-001',
            f'PT-{self.trip.id}-002',
            f'PT-{self.trip.id}-003',
            f'PT-{self.trip.id}-004',
            f'PT-{self.trip.id}-005',
        ])


class ApiContractRegressionTests(TestCase):
    """Protect the fields consumed by the current web trip and ticket screens."""

    def setUp(self):
        self.office = Office.objects.create(name='HQ', city='Algiers')
        self.office_b = Office.objects.create(name='Branch', city='Oran')
        self.admin = User.objects.create_superuser(
            phone='0550000010', password='pass',
            first_name='Admin', last_name='X',
        )
        self.conductor = User.objects.create_user(
            phone='0550000011', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office,
        )
        bus = Bus.objects.create(
            plate_number='BUS-02', capacity=50, office=self.office,
        )
        self.trip = Trip.objects.create(
            origin_office=self.office,
            destination_office=self.office_b,
            conductor=self.conductor,
            bus=bus,
            departure_datetime=timezone.now(),
            status='in_progress',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )
        PassengerTicket.objects.create(
            trip=self.trip,
            ticket_number='PT-1-001',
            passenger_name='Passenger',
            price=1000,
            payment_source='cash',
            created_by=self.conductor,
        )
        TripExpense.objects.create(
            trip=self.trip,
            description='Fuel',
            amount=300,
            category='fuel',
            created_by=self.conductor,
        )
        self.client = APIClient()

    def test_trip_list_contains_web_fields(self):
        self.client.force_authenticate(self.admin)
        resp = self.client.get('/api/trips/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        trip = resp.data['results'][0]
        self.assertIn('origin_office_name', trip)
        self.assertIn('destination_office_name', trip)
        self.assertIn('bus_plate', trip)
        self.assertIn('conductor_name', trip)
        self.assertIn('passenger_count', trip)
        self.assertIn('cargo_count', trip)
        self.assertIn('expense_total', trip)

    def test_trip_detail_contains_web_fields(self):
        self.client.force_authenticate(self.admin)
        resp = self.client.get(f'/api/trips/{self.trip.id}/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertIn('origin_office_name', resp.data)
        self.assertIn('destination_office_name', resp.data)
        self.assertIn('bus_plate', resp.data)
        self.assertIn('created_at', resp.data)
        self.assertIn('updated_at', resp.data)

    def test_ticket_list_contains_web_fields(self):
        self.client.force_authenticate(self.admin)
        resp = self.client.get('/api/tickets/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        ticket = resp.data['results'][0]
        self.assertIn('trip', ticket)
        self.assertIn('payment_source', ticket)
        self.assertIn('created_at', ticket)
        self.assertIn('created_by_name', ticket)
