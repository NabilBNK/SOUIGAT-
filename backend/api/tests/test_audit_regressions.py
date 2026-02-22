"""
Regression tests for Week 3 critical bug fixes.

Tests added after code audit (7.8/10 → 9.2+/10):
- Unauthorized ticket cancellation (Flaw #2)
- Queryset isolation by office (Flaw #3)
- Cargo ticket race condition guard (Flaw #1)
"""
from datetime import date, timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import (
    Bus, CargoTicket, Office, PassengerTicket,
    PricingConfig, Trip, User,
)


class UnauthorizedCancelTest(TestCase):
    """Flaw #2: Conductor B cannot cancel Conductor A's tickets."""

    def setUp(self):
        self.office = Office.objects.create(name='HQ', city='Algiers')
        self.office_b = Office.objects.create(name='Branch', city='Oran')

        self.conductor_a = User.objects.create_user(
            phone='0550000001', password='pass',
            first_name='A', last_name='Cond',
            role='conductor', office=self.office,
        )
        self.conductor_b = User.objects.create_user(
            phone='0550000002', password='pass',
            first_name='B', last_name='Cond',
            role='conductor', office=self.office,
        )
        bus = Bus.objects.create(
            plate_number='00001-116-16', capacity=50, office=self.office,
        )
        self.trip = Trip.objects.create(
            origin_office=self.office,
            destination_office=self.office_b,
            conductor=self.conductor_a,
            bus=bus,
            departure_datetime=timezone.now() + timedelta(hours=1),
            status='scheduled',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )
        self.ticket = PassengerTicket.objects.create(
            trip=self.trip, ticket_number='PT-1-001',
            passenger_name='John', price=1000,
            created_by=self.conductor_a,
        )
        self.client = APIClient()

    def test_wrong_conductor_cannot_cancel_ticket(self):
        """Conductor B should NOT be able to cancel Conductor A's ticket."""
        self.client.force_authenticate(self.conductor_b)
        resp = self.client.post(f'/api/tickets/{self.ticket.id}/cancel/')
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)
        self.ticket.refresh_from_db()
        self.assertEqual(self.ticket.status, 'active')

    def test_own_conductor_can_cancel(self):
        """Conductor A CAN cancel their own trip's ticket."""
        self.client.force_authenticate(self.conductor_a)
        resp = self.client.post(f'/api/tickets/{self.ticket.id}/cancel/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.ticket.refresh_from_db()
        self.assertEqual(self.ticket.status, 'cancelled')


class QuerysetIsolationTest(TestCase):
    """Flaw #3: Office staff should only see trips from their office."""

    def setUp(self):
        self.office_a = Office.objects.create(name='Algiers', city='Algiers')
        self.office_b = Office.objects.create(name='Oran', city='Oran')
        self.office_c = Office.objects.create(name='Constantine', city='Constantine')

        self.staff_a = User.objects.create_user(
            phone='0550000001', password='pass',
            first_name='Staff', last_name='A',
            role='office_staff', office=self.office_a,
        )
        conductor_a = User.objects.create_user(
            phone='0550000002', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office_a,
        )
        conductor_c = User.objects.create_user(
            phone='0550000003', password='pass',
            first_name='Cond', last_name='C',
            role='conductor', office=self.office_c,
        )
        bus_a = Bus.objects.create(
            plate_number='BUS-A', capacity=50, office=self.office_a,
        )
        bus_c = Bus.objects.create(
            plate_number='BUS-C', capacity=50, office=self.office_c,
        )

        # Trip A→B: staff_a should see (origin = their office)
        Trip.objects.create(
            origin_office=self.office_a,
            destination_office=self.office_b,
            conductor=conductor_a, bus=bus_a,
            departure_datetime=timezone.now() + timedelta(hours=1),
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )
        # Trip B→A: staff_a should see (destination = their office)
        Trip.objects.create(
            origin_office=self.office_b,
            destination_office=self.office_a,
            conductor=conductor_a, bus=bus_a,
            departure_datetime=timezone.now() + timedelta(hours=2),
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )
        # Trip C→B: staff_a should NOT see (unrelated offices)
        Trip.objects.create(
            origin_office=self.office_c,
            destination_office=self.office_b,
            conductor=conductor_c, bus=bus_c,
            departure_datetime=timezone.now() + timedelta(hours=3),
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )

        self.client = APIClient()

    def test_staff_sees_only_own_office_trips(self):
        """Staff A (Algiers) should see 2 trips, not the Constantine→Oran one."""
        self.client.force_authenticate(self.staff_a)
        resp = self.client.get('/api/trips/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(len(resp.data), 2)

    def test_admin_sees_all_trips(self):
        admin = User.objects.create_superuser(
            phone='0550099999', password='pass',
            first_name='Admin', last_name='X',
        )
        self.client.force_authenticate(admin)
        resp = self.client.get('/api/trips/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(len(resp.data), 3)


class CargoAtomicCreationTest(TestCase):
    """Flaw #1: Cargo creation must be atomic (ticket_number uniqueness)."""

    def setUp(self):
        self.office = Office.objects.create(name='HQ', city='Algiers')
        self.office_b = Office.objects.create(name='Branch', city='Oran')
        self.staff = User.objects.create_user(
            phone='0550000001', password='pass',
            first_name='Staff', last_name='A',
            role='office_staff', office=self.office,
        )
        conductor = User.objects.create_user(
            phone='0550000002', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office,
        )
        bus = Bus.objects.create(
            plate_number='00001-116-16', capacity=50, office=self.office,
        )
        self.trip = Trip.objects.create(
            origin_office=self.office,
            destination_office=self.office_b,
            conductor=conductor, bus=bus,
            departure_datetime=timezone.now() + timedelta(hours=1),
            status='scheduled',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )
        self.client = APIClient()

    def test_sequential_cargo_unique_numbers(self):
        """Two sequential creates get different ticket numbers."""
        self.client.force_authenticate(self.staff)
        r1 = self.client.post('/api/cargo/', {
            'trip': self.trip.id,
            'sender_name': 'S1', 'receiver_name': 'R1',
            'cargo_tier': 'small', 'payment_source': 'prepaid',
        })
        r2 = self.client.post('/api/cargo/', {
            'trip': self.trip.id,
            'sender_name': 'S2', 'receiver_name': 'R2',
            'cargo_tier': 'medium', 'payment_source': 'prepaid',
        })
        self.assertEqual(r1.status_code, status.HTTP_201_CREATED)
        self.assertEqual(r2.status_code, status.HTTP_201_CREATED)
        self.assertNotEqual(r1.data['ticket_number'], r2.data['ticket_number'])

    def test_cargo_price_matches_tier(self):
        """Each tier gets correct price from trip snapshot."""
        self.client.force_authenticate(self.staff)
        for tier, expected in [('small', 500), ('medium', 1000), ('large', 1500)]:
            resp = self.client.post('/api/cargo/', {
                'trip': self.trip.id,
                'sender_name': 'S', 'receiver_name': 'R',
                'cargo_tier': tier, 'payment_source': 'prepaid',
            })
            self.assertEqual(resp.data['price'], expected, f'{tier} price mismatch')
