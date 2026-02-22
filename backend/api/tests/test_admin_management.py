"""Tests for T20: Admin management endpoints."""
from datetime import date

from django.test import TestCase
from rest_framework import status
from rest_framework.test import APIClient

from api.models import AuditLog, Bus, Office, PricingConfig, User


class AdminManagementTests(TestCase):
    """Admin CRUD for users, buses, offices, pricing, and audit log."""

    def setUp(self):
        self.office = Office.objects.create(name='Algiers', city='Algiers')

        self.admin = User.objects.create_superuser(
            phone='0550000001', password='adminpass',
            first_name='Admin', last_name='Root',
        )
        self.staff = User.objects.create_user(
            phone='0550000002', password='pass',
            first_name='Staff', last_name='A',
            role='office_staff', office=self.office,
        )
        self.bus = Bus.objects.create(
            plate_number='BUS-01', capacity=50, office=self.office,
        )
        self.client = APIClient()

    # ---- User CRUD ----

    def test_admin_creates_user_with_hashed_password(self):
        """Admin creates a user; password is hashed, not stored plaintext."""
        self.client.force_authenticate(self.admin)
        resp = self.client.post('/api/admin/users/', {
            'phone': '0550000010',
            'first_name': 'New', 'last_name': 'User',
            'role': 'conductor', 'password': 'testpass123',
        })
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED)
        user = User.objects.get(phone='0550000010')
        self.assertTrue(user.check_password('testpass123'))
        self.assertNotEqual(user.password, 'testpass123')

    def test_admin_deactivates_user(self):
        """DELETE soft-deactivates user (is_active=False), not hard delete."""
        self.client.force_authenticate(self.admin)
        resp = self.client.delete(f'/api/admin/users/{self.staff.id}/')
        self.assertEqual(resp.status_code, status.HTTP_204_NO_CONTENT)
        self.staff.refresh_from_db()
        self.assertFalse(self.staff.is_active)

    # ---- Bus CRUD ----

    def test_admin_creates_bus(self):
        """Admin creates a bus with unique plate."""
        self.client.force_authenticate(self.admin)
        resp = self.client.post('/api/admin/buses/', {
            'plate_number': 'BUS-NEW',
            'capacity': 30,
            'office': self.office.id,
        })
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED)
        self.assertTrue(Bus.objects.filter(plate_number='BUS-NEW').exists())

    def test_duplicate_plate_rejected(self):
        """Duplicate plate_number returns 400."""
        self.client.force_authenticate(self.admin)
        resp = self.client.post('/api/admin/buses/', {
            'plate_number': 'BUS-01',
            'capacity': 30,
            'office': self.office.id,
        })
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)

    # ---- Office CRUD ----

    def test_admin_creates_office(self):
        """Admin creates a new office."""
        self.client.force_authenticate(self.admin)
        resp = self.client.post('/api/admin/offices/', {
            'name': 'Constantine', 'city': 'Constantine',
        })
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED)

    # ---- Pricing CRUD ----

    def test_admin_creates_pricing(self):
        """Admin creates pricing config."""
        self.client.force_authenticate(self.admin)
        office_b = Office.objects.create(name='Oran', city='Oran')
        resp = self.client.post('/api/admin/pricing/', {
            'origin_office': self.office.id,
            'destination_office': office_b.id,
            'passenger_price': 1500,
            'cargo_small_price': 500,
            'cargo_medium_price': 1000,
            'cargo_large_price': 1500,
            'effective_from': '2025-01-01',
        })
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED)

    def test_pricing_same_origin_dest_rejected(self):
        """Origin == destination is rejected."""
        self.client.force_authenticate(self.admin)
        resp = self.client.post('/api/admin/pricing/', {
            'origin_office': self.office.id,
            'destination_office': self.office.id,
            'passenger_price': 1500,
            'cargo_small_price': 500,
            'cargo_medium_price': 1000,
            'cargo_large_price': 1500,
            'effective_from': '2025-01-01',
        })
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)

    # ---- Audit Log (read-only) ----

    def test_admin_lists_audit_logs(self):
        """Admin can list audit log entries."""
        AuditLog.objects.create(
            user=self.admin, action='create',
            table_name='users', record_id=1,
        )
        self.client.force_authenticate(self.admin)
        resp = self.client.get('/api/admin/audit-log/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertTrue(len(resp.data) >= 1)

    # ---- Permission guard ----

    def test_non_admin_denied(self):
        """Non-admin users get 403 on all admin endpoints."""
        self.client.force_authenticate(self.staff)
        endpoints = [
            '/api/admin/users/',
            '/api/admin/buses/',
            '/api/admin/offices/',
            '/api/admin/pricing/',
            '/api/admin/audit-log/',
        ]
        for url in endpoints:
            resp = self.client.get(url)
            self.assertEqual(
                resp.status_code, status.HTTP_403_FORBIDDEN,
                f'{url} should be 403 for non-admin',
            )
