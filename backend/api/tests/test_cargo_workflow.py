from datetime import date, timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import Bus, CargoTicket, Office, PricingConfig, Trip, User


class CargoWorkflowTests(TestCase):
    """Tests for cargo CRUD, state transitions, and delivery permissions."""

    def setUp(self):
        self.office_a = Office.objects.create(name='Office A', city='Algiers')
        self.office_b = Office.objects.create(name='Office B', city='Oran')

        self.staff_a = User.objects.create_user(
            phone='0550000001', password='pass',
            first_name='Staff', last_name='A',
            role='office_staff', office=self.office_a,
        )
        self.staff_b = User.objects.create_user(
            phone='0550000002', password='pass',
            first_name='Staff', last_name='B',
            role='office_staff', office=self.office_b,
        )
        self.conductor = User.objects.create_user(
            phone='0550000003', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office_a,
        )

        self.bus = Bus.objects.create(
            plate_number='00001-116-16', capacity=50, office=self.office_a,
        )

        PricingConfig.objects.create(
            origin_office=self.office_a,
            destination_office=self.office_b,
            passenger_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
            effective_from=date.today(),
        )

        self.trip = Trip.objects.create(
            origin_office=self.office_a,
            destination_office=self.office_b,
            conductor=self.conductor,
            bus=self.bus,
            departure_datetime=timezone.now() + timedelta(hours=1),
            status='scheduled',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )

        self.client = APIClient()

    def _make_cargo(self, **overrides):
        defaults = dict(
            trip=self.trip,
            ticket_number=f'CT-{CargoTicket.objects.count() + 1}',
            sender_name='Sender',
            receiver_name='Receiver',
            cargo_tier='medium',
            price=1000,
            payment_source='prepaid',
            created_by=self.staff_a,
            status='created',
        )
        defaults.update(overrides)
        return CargoTicket.objects.create(**defaults)

    def test_create_cargo_success(self):
        self.client.force_authenticate(self.staff_a)
        resp = self.client.post('/api/cargo/', {
            'trip': self.trip.id,
            'sender_name': 'Sender',
            'receiver_name': 'Receiver',
            'cargo_tier': 'medium',
            'payment_source': 'prepaid',
        })
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED)
        cargo = CargoTicket.objects.first()
        self.assertEqual(cargo.price, 1000)  # medium price

    def test_transition_created_to_in_transit(self):
        cargo = self._make_cargo()
        self.client.force_authenticate(self.conductor)
        resp = self.client.post(f'/api/cargo/{cargo.id}/transition/', {
            'new_status': 'in_transit',
        })
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        cargo.refresh_from_db()
        self.assertEqual(cargo.status, 'in_transit')

    def test_deliver_cargo_destination_staff_success(self):
        """Staff B (destination) can deliver."""
        cargo = self._make_cargo(status='arrived')
        self.client.force_authenticate(self.staff_b)
        resp = self.client.post(f'/api/cargo/{cargo.id}/deliver/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        cargo.refresh_from_db()
        self.assertEqual(cargo.status, 'delivered')
        self.assertEqual(cargo.delivered_by, self.staff_b)

    def test_deliver_cargo_origin_staff_forbidden(self):
        """Staff A (origin) CANNOT deliver."""
        cargo = self._make_cargo(status='arrived')
        self.client.force_authenticate(self.staff_a)
        resp = self.client.post(f'/api/cargo/{cargo.id}/deliver/')
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)

    def test_invalid_transition_rejected(self):
        """Cannot jump created → delivered (must go through in_transit → arrived)."""
        cargo = self._make_cargo()
        self.client.force_authenticate(self.staff_b)
        resp = self.client.post(f'/api/cargo/{cargo.id}/transition/', {
            'new_status': 'delivered',
        })
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)

    def test_cargo_price_by_tier(self):
        """Small tier gets small price from trip snapshot."""
        self.client.force_authenticate(self.staff_a)
        resp = self.client.post('/api/cargo/', {
            'trip': self.trip.id,
            'sender_name': 'S',
            'receiver_name': 'R',
            'cargo_tier': 'small',
            'payment_source': 'prepaid',
        })
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED)
        self.assertEqual(resp.data['price'], 500)
