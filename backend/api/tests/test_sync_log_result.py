"""Tests for GET /api/sync/log/{key}/ — sync log result lookup."""
from datetime import date, timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import Bus, Office, PricingConfig, SyncLog, Trip, User


class SyncLogResultTests(TestCase):
    """GET /api/sync/log/{key}/ — idempotency key lookup for 'already_processed' handler."""

    def setUp(self):
        self.office = Office.objects.create(name='HQ', city='Algiers')
        self.office_b = Office.objects.create(name='Branch', city='Oran')
        self.conductor = User.objects.create_user(
            phone='0550000001', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office,
        )
        self.other_conductor = User.objects.create_user(
            phone='0550000002', password='pass',
            first_name='Cond', last_name='B',
            role='conductor', office=self.office,
        )
        self.staff = User.objects.create_user(
            phone='0550000003', password='pass',
            first_name='Staff', last_name='A',
            role='office_staff', office=self.office,
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
            conductor=self.conductor,
            bus=bus,
            departure_datetime=timezone.now() + timedelta(hours=1),
            status='in_progress',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )
        self.client = APIClient()

        # Create test sync log entries
        self.accepted_log = SyncLog.objects.create(
            key='sync-1-abc123def456',
            conductor=self.conductor,
            trip=self.trip,
            accepted=1,
            quarantined=0,
        )
        self.quarantined_log = SyncLog.objects.create(
            key='sync-1-quarantined789',
            conductor=self.conductor,
            trip=self.trip,
            accepted=0,
            quarantined=1,
        )
        self.other_conductor_log = SyncLog.objects.create(
            key='sync-2-other-conductor',
            conductor=self.other_conductor,
            trip=self.trip,
            accepted=1,
            quarantined=0,
        )

    def test_accepted_key_returns_200(self):
        """GET with a valid accepted key returns 200 with correct data."""
        self.client.force_authenticate(self.conductor)
        resp = self.client.get(f'/api/sync/log/{self.accepted_log.key}/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['key'], self.accepted_log.key)
        self.assertEqual(resp.data['accepted'], 1)
        self.assertEqual(resp.data['quarantined'], 0)

    def test_quarantined_key_returns_200(self):
        """GET with a valid quarantined key returns 200 with correct split."""
        self.client.force_authenticate(self.conductor)
        resp = self.client.get(f'/api/sync/log/{self.quarantined_log.key}/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['accepted'], 0)
        self.assertEqual(resp.data['quarantined'], 1)

    def test_unknown_key_returns_404(self):
        """GET with an unknown key returns 404."""
        self.client.force_authenticate(self.conductor)
        resp = self.client.get('/api/sync/log/nonexistent-key-abc123/')
        self.assertEqual(resp.status_code, status.HTTP_404_NOT_FOUND)

    def test_other_conductor_key_returns_404(self):
        """Conductor cannot see another conductor's sync log (JWT scoping)."""
        self.client.force_authenticate(self.conductor)
        resp = self.client.get(f'/api/sync/log/{self.other_conductor_log.key}/')
        self.assertEqual(resp.status_code, status.HTTP_404_NOT_FOUND)

    def test_unauthenticated_returns_401(self):
        """Unauthenticated request returns 401."""
        resp = self.client.get(f'/api/sync/log/{self.accepted_log.key}/')
        self.assertEqual(resp.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_staff_cannot_access(self):
        """Office staff (no sync_batch permission) returns 403."""
        self.client.force_authenticate(self.staff)
        resp = self.client.get(f'/api/sync/log/{self.accepted_log.key}/')
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)

    def test_long_key_varchar100(self):
        """Keys up to 100 chars are stored and retrieved correctly."""
        # Simulate BigAutoField userId (19 digits) + full SHA-256 (64 hex)
        long_key = 'sync-1234567890123456789-' + 'a' * 64  # 89 chars
        log = SyncLog.objects.create(
            key=long_key,
            conductor=self.conductor,
            trip=self.trip,
            accepted=1,
            quarantined=0,
        )
        self.client.force_authenticate(self.conductor)
        resp = self.client.get(f'/api/sync/log/{long_key}/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['key'], long_key)
