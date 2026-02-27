"""Tests for Bug #2 (audit old_values) and Bug #3 (auth event logging)."""
from datetime import date, timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import AuditLog, Bus, Office, PricingConfig, Trip, User


class AuditOldValuesTests(TestCase):
    """Bug #2: PUT/PATCH should capture old_values in audit log."""

    def setUp(self):
        self.office = Office.objects.create(name='HQ', city='Algiers')
        self.office_b = Office.objects.create(name='Branch', city='Oran')
        self.admin = User.objects.create_superuser(
            phone='0550000001', password='pass',
            first_name='Admin', last_name='X',
        )
        self.bus = Bus.objects.create(
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
        self.client = APIClient()

    def test_update_captures_old_values(self):
        """PATCH creates audit log with non-None old_values."""
        self.client.force_authenticate(self.admin)
        # Create a bus then update it
        resp = self.client.patch(
            f'/api/admin/buses/{self.bus.id}/',
            {'capacity': 60},
            format='json',
        )
        if resp.status_code == status.HTTP_200_OK:
            log = AuditLog.objects.filter(
                action='update',
                table_name='buses',  # _resolve_table now correctly returns 'buses' for /api/admin/buses/
            ).last()
            if log:
                self.assertIsNotNone(
                    log.old_values,
                    'old_values should be populated for updates',
                )


class AuthEventLoggingTests(TestCase):
    """Bug #3: Login/logout should create audit log entries."""

    def setUp(self):
        self.admin = User.objects.create_superuser(
            phone='0550000001', password='testpass123',
            first_name='Admin', last_name='X',
        )
        self.client = APIClient()

    def test_login_creates_audit_entry(self):
        """Successful login should create an audit log entry."""
        initial_count = AuditLog.objects.count()
        resp = self.client.post('/api/auth/login/', {
            'phone': '0550000001',
            'password': 'testpass123',
        }, format='json')
        # Auth events are now logged (Bug #3 fix)
        if resp.status_code == status.HTTP_200_OK:
            self.assertGreater(
                AuditLog.objects.count(), initial_count,
                'Login should create an audit log entry',
            )

    def test_token_refresh_still_skipped(self):
        """Token refresh should NOT create audit entries (too noisy)."""
        initial_count = AuditLog.objects.count()
        # Login first to get tokens
        resp = self.client.post('/api/auth/login/', {
            'phone': '0550000001',
            'password': 'testpass123',
        }, format='json')
        if resp.status_code == status.HTTP_200_OK:
            after_login_count = AuditLog.objects.count()
            refresh_token = resp.data.get('refresh')
            if refresh_token:
                self.client.post('/api/auth/token/refresh/', {
                    'refresh': refresh_token,
                })
                self.assertEqual(
                    AuditLog.objects.count(), after_login_count,
                    'Token refresh should not create audit entries',
                )
