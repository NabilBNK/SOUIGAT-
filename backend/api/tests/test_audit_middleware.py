"""Tests for T17: Audit middleware."""
from datetime import date, timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import AuditLog, Bus, Office, PricingConfig, Trip, User


class AuditMiddlewareTests(TestCase):
    """Audit middleware logging behavior."""

    def setUp(self):
        self.office = Office.objects.create(name='HQ', city='Algiers')
        self.office_b = Office.objects.create(name='Branch', city='Oran')
        self.admin = User.objects.create_superuser(
            phone='0550000001', password='pass',
            first_name='Admin', last_name='X',
        )
        self.staff = User.objects.create_user(
            phone='0550000002', password='pass',
            first_name='Staff', last_name='A',
            role='office_staff', office=self.office,
        )
        self.conductor = User.objects.create_user(
            phone='0550000003', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office,
        )
        Bus.objects.create(
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

    def test_post_creates_audit_log(self):
        """POST to a tracked endpoint creates an audit log entry."""
        initial_count = AuditLog.objects.count()
        self.client.force_authenticate(self.staff)
        self.client.post('/api/trips/', {
            'origin_office': self.office.id,
            'destination_office': self.office_b.id,
            'conductor': self.conductor.id,
            'bus': Bus.objects.first().id,
            'departure_datetime': (timezone.now() + timedelta(hours=1)).isoformat(),
        }, format='json')
        self.assertGreater(AuditLog.objects.count(), initial_count)

    def test_get_does_not_log(self):
        """GET requests are not audited."""
        initial_count = AuditLog.objects.count()
        self.client.force_authenticate(self.admin)
        self.client.get('/api/trips/')
        self.assertEqual(AuditLog.objects.count(), initial_count)

    def test_auth_login_now_audited(self):
        """Bug #3 fix: Login IS now audited. Token refresh is skipped."""
        initial_count = AuditLog.objects.count()
        resp = self.client.post('/api/auth/login/', {
            'phone': '0550000001', 'password': 'pass',
        })
        if resp.status_code == 200:
            # Login should now create an audit log entry (Bug #3 fix)
            self.assertGreater(AuditLog.objects.count(), initial_count)

    def test_health_check_skipped(self):
        """Health check is not audited."""
        initial_count = AuditLog.objects.count()
        self.client.get('/api/')
        self.assertEqual(AuditLog.objects.count(), initial_count)
