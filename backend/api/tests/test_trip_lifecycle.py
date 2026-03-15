from datetime import date, timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import Bus, Office, PricingConfig, Trip, User


class TripLifecycleTests(TestCase):
    """Tests for Trip CRUD, start/complete/cancel, and concurrency guards."""

    def setUp(self):
        self.office_a = Office.objects.create(name='Algiers HQ', city='Algiers')
        self.office_b = Office.objects.create(name='Oran Branch', city='Oran')

        self.staff = User.objects.create_user(
            phone='0550000001', password='pass',
            first_name='Staff', last_name='A',
            role='office_staff', office=self.office_a,
        )
        self.conductor = User.objects.create_user(
            phone='0550000002', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office_a,
        )
        self.conductor_b = User.objects.create_user(
            phone='0550000003', password='pass',
            first_name='Cond', last_name='B',
            role='conductor', office=self.office_a,
        )
        self.admin = User.objects.create_user(
            phone='0550000004', password='pass',
            first_name='Admin', last_name='A',
            role='admin',
        )

        self.bus = Bus.objects.create(
            plate_number='00001-116-16', capacity=50, office=self.office_a,
        )
        self.bus_b = Bus.objects.create(
            plate_number='00002-116-16', capacity=50, office=self.office_a,
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

        self.client = APIClient()

    def _make_trip(self, **overrides):
        defaults = dict(
            origin_office=self.office_a,
            destination_office=self.office_b,
            conductor=self.conductor,
            bus=self.bus,
            departure_datetime=timezone.now() + timedelta(minutes=15),
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )
        defaults.update(overrides)
        return Trip.objects.create(**defaults)

    # ---- T9.1  Create trip (staff) --------------------------------

    def test_create_trip_success(self):
        """Staff creates trip; pricing snapshot is frozen from PricingConfig."""
        self.client.force_authenticate(self.staff)
        resp = self.client.post('/api/trips/', {
            'origin_office': self.office_a.id,
            'destination_office': self.office_b.id,
            'conductor': self.conductor.id,
            'bus': self.bus.id,
            'departure_datetime': (timezone.now() + timedelta(hours=1)).isoformat(),
        })
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED)
        trip = Trip.objects.first()
        self.assertEqual(trip.passenger_base_price, 1000)
        self.assertEqual(trip.status, 'scheduled')

    def test_create_trip_wrong_origin_forbidden(self):
        """Staff A cannot create trip from Office B."""
        self.client.force_authenticate(self.staff)
        resp = self.client.post('/api/trips/', {
            'origin_office': self.office_b.id,
            'destination_office': self.office_a.id,
            'conductor': self.conductor.id,
            'bus': self.bus.id,
            'departure_datetime': (timezone.now() + timedelta(hours=1)).isoformat(),
        })
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)

    # ---- T9.2  Start trip (conductor) -----------------------------

    def test_start_trip_success(self):
        trip = self._make_trip()
        self.client.force_authenticate(self.conductor)
        resp = self.client.post(f'/api/trips/{trip.id}/start/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        trip.refresh_from_db()
        self.assertEqual(trip.status, 'in_progress')

    def test_start_trip_wrong_conductor(self):
        trip = self._make_trip()
        self.client.force_authenticate(self.conductor_b)
        resp = self.client.post(f'/api/trips/{trip.id}/start/')
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)

    def test_start_trip_bus_already_active(self):
        """Cannot start if bus is on another active trip."""
        self._make_trip(conductor=self.conductor_b, status='in_progress')
        trip2 = self._make_trip(conductor=self.conductor, bus=self.bus)
        self.client.force_authenticate(self.conductor)
        resp = self.client.post(f'/api/trips/{trip2.id}/start/')
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn('Bus', resp.data[0] if isinstance(resp.data, list) else str(resp.data))

    def test_start_trip_conductor_already_active(self):
        """Cannot start if conductor already has an active trip."""
        self._make_trip(bus=self.bus_b, status='in_progress')
        trip2 = self._make_trip(bus=self.bus)
        self.client.force_authenticate(self.conductor)
        resp = self.client.post(f'/api/trips/{trip2.id}/start/')
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn('Conductor', resp.data[0] if isinstance(resp.data, list) else str(resp.data))

    # ---- T9.3  Complete / Cancel ----------------------------------

    def test_complete_trip(self):
        trip = self._make_trip(
            status='in_progress',
            departure_datetime=timezone.now() - timedelta(hours=2),
        )
        self.client.force_authenticate(self.conductor)
        resp = self.client.post(f'/api/trips/{trip.id}/complete/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        trip.refresh_from_db()
        self.assertEqual(trip.status, 'completed')
        self.assertIsNotNone(trip.arrival_datetime)

    def test_complete_trip_before_scheduled_departure_clamps_arrival(self):
        trip = self._make_trip(
            status='in_progress',
            departure_datetime=timezone.now() + timedelta(minutes=10),
        )
        self.client.force_authenticate(self.conductor)

        resp = self.client.post(f'/api/trips/{trip.id}/complete/')

        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        trip.refresh_from_db()
        self.assertEqual(trip.status, 'completed')
        self.assertGreater(trip.arrival_datetime, trip.departure_datetime)

    def test_cancel_trip(self):
        trip = self._make_trip()
        self.client.force_authenticate(self.staff)
        resp = self.client.post(f'/api/trips/{trip.id}/cancel/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        trip.refresh_from_db()
        self.assertEqual(trip.status, 'cancelled')

    def test_cannot_start_completed_trip(self):
        trip = self._make_trip(status='completed')
        self.client.force_authenticate(self.conductor)
        resp = self.client.post(f'/api/trips/{trip.id}/start/')
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)

    def test_admin_can_delete_scheduled_trip(self):
        trip = self._make_trip()
        self.client.force_authenticate(self.admin)
        resp = self.client.delete(f'/api/trips/{trip.id}/')
        self.assertEqual(resp.status_code, status.HTTP_204_NO_CONTENT)
        self.assertFalse(Trip.all_objects.filter(pk=trip.id).exists())

    def test_admin_can_delete_cancelled_trip(self):
        trip = self._make_trip(status='cancelled')
        self.client.force_authenticate(self.admin)
        resp = self.client.delete(f'/api/trips/{trip.id}/')
        self.assertEqual(resp.status_code, status.HTTP_204_NO_CONTENT)
        self.assertFalse(Trip.all_objects.filter(pk=trip.id).exists())

    def test_admin_cannot_delete_in_progress_trip(self):
        trip = self._make_trip(status='in_progress')
        self.client.force_authenticate(self.admin)
        resp = self.client.delete(f'/api/trips/{trip.id}/')
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)
        trip.refresh_from_db()
        self.assertEqual(trip.status, 'in_progress')
