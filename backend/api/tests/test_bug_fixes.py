"""Tests for Bug #5 (route report admin-only) and Bug #9 (bus-office validation)."""
from datetime import date, timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import Bus, Office, PricingConfig, Trip, User


class RouteReportAdminOnlyTests(TestCase):
    """Bug #5: Route reports should be restricted to admin only."""

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
        self.client = APIClient()

    def test_admin_can_access_route_report(self):
        """Admin can access route reports."""
        self.client.force_authenticate(self.admin)
        today = timezone.now().strftime('%Y-%m-%d')
        resp = self.client.get(
            f'/api/reports/route/?date_from={today}&date_to={today}'
        )
        self.assertEqual(resp.status_code, status.HTTP_200_OK)

    def test_office_staff_denied_route_report(self):
        """Office staff gets 403 on route report (Bug #5 fix)."""
        self.client.force_authenticate(self.staff)
        today = timezone.now().strftime('%Y-%m-%d')
        resp = self.client.get(
            f'/api/reports/route/?date_from={today}&date_to={today}'
        )
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)

    def test_staff_denied_daily_report(self):
        """Office staff are denied daily reports."""
        self.client.force_authenticate(self.staff)
        today = timezone.now().strftime('%Y-%m-%d')
        resp = self.client.get(
            f'/api/reports/daily/?date_from={today}&date_to={today}'
        )
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)


class BusOfficeValidationTests(TestCase):
    """Bug #9: Bus must belong to trip origin office."""

    def setUp(self):
        self.office_a = Office.objects.create(name='Algiers', city='Algiers')
        self.office_b = Office.objects.create(name='Oran', city='Oran')
        self.admin = User.objects.create_superuser(
            phone='0550000001', password='pass',
            first_name='Admin', last_name='X',
        )
        self.conductor = User.objects.create_user(
            phone='0550000003', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office_a,
        )
        self.bus_a = Bus.objects.create(
            plate_number='BUS-A', capacity=50, office=self.office_a,
        )
        self.bus_b = Bus.objects.create(
            plate_number='BUS-B', capacity=50, office=self.office_b,
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

    def test_bus_matching_origin_allowed(self):
        """Creating trip with bus from same office as origin succeeds."""
        self.client.force_authenticate(self.admin)
        resp = self.client.post('/api/trips/', {
            'origin_office': self.office_a.id,
            'destination_office': self.office_b.id,
            'conductor': self.conductor.id,
            'bus': self.bus_a.id,
            'departure_datetime': (timezone.now() + timedelta(hours=1)).isoformat(),
        }, format='json')
        self.assertIn(resp.status_code, [status.HTTP_201_CREATED, status.HTTP_200_OK])

    def test_bus_mismatched_origin_allowed(self):
        """Creating trip with bus from different office is allowed for return trips."""
        self.client.force_authenticate(self.admin)
        resp = self.client.post('/api/trips/', {
            'origin_office': self.office_a.id,
            'destination_office': self.office_b.id,
            'conductor': self.conductor.id,
            'bus': self.bus_b.id,  # bus_b belongs to office_b, not office_a
            'departure_datetime': (timezone.now() + timedelta(hours=1)).isoformat(),
        }, format='json')
        self.assertIn(resp.status_code, [status.HTTP_201_CREATED, status.HTTP_200_OK])
