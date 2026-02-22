"""Tests for T15: Report endpoints."""
from datetime import timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import (
    Bus, CargoTicket, Office, PassengerTicket,
    PricingConfig, Trip, TripExpense, User,
)


class ReportTests(TestCase):
    """Daily, trip, and route report accuracy and scoping."""

    def setUp(self):
        self.office = Office.objects.create(name='Algiers', city='Algiers')
        self.office_b = Office.objects.create(name='Oran', city='Oran')

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
        # Create test data
        PassengerTicket.objects.create(
            trip=self.trip, ticket_number='PT-1-001',
            passenger_name='P1', price=1000, created_by=self.conductor,
        )
        PassengerTicket.objects.create(
            trip=self.trip, ticket_number='PT-1-002',
            passenger_name='P2', price=1000, created_by=self.conductor,
        )
        CargoTicket.objects.create(
            trip=self.trip, ticket_number='CT-1-001',
            sender_name='S1', receiver_name='R1',
            cargo_tier='small', price=500, created_by=self.conductor,
        )
        TripExpense.objects.create(
            trip=self.trip, description='Fuel',
            amount=300, created_by=self.conductor,
        )
        self.client = APIClient()

    def test_daily_report_aggregation(self):
        """Daily report shows correct totals."""
        self.client.force_authenticate(self.admin)
        today = timezone.now().strftime('%Y-%m-%d')
        resp = self.client.get(f'/api/reports/daily/?date_from={today}&date_to={today}')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['passenger_revenue'], 2000)
        self.assertEqual(resp.data['cargo_revenue'], 500)
        self.assertEqual(resp.data['expense_total'], 300)
        self.assertEqual(resp.data['net'], 2200)
        self.assertEqual(resp.data['ticket_count'], 2)

    def test_trip_report_breakdown(self):
        """Trip report includes ticket and expense details."""
        self.client.force_authenticate(self.admin)
        resp = self.client.get(f'/api/reports/trip/{self.trip.id}/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['passenger_revenue'], 2000)
        self.assertEqual(resp.data['cargo_revenue'], 500)
        self.assertEqual(resp.data['ticket_count'], 2)
        self.assertEqual(len(resp.data['expenses']), 1)

    def test_route_report(self):
        """Route report groups by origin→destination."""
        self.client.force_authenticate(self.admin)
        today = timezone.now().strftime('%Y-%m-%d')
        resp = self.client.get(f'/api/reports/route/?date_from={today}&date_to={today}')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertTrue(len(resp.data) >= 1)
        self.assertEqual(resp.data[0]['trip_count'], 1)
        self.assertIn('total_revenue', resp.data[0])

    def test_staff_scoped_to_office(self):
        """Staff only sees reports for their office's trips."""
        self.client.force_authenticate(self.staff)
        today = timezone.now().strftime('%Y-%m-%d')
        resp = self.client.get(f'/api/reports/daily/?date_from={today}&date_to={today}')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['trip_count'], 1)

    def test_conductor_cannot_access_reports(self):
        """Conductors are denied access to reports."""
        self.client.force_authenticate(self.conductor)
        resp = self.client.get('/api/reports/daily/')
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)
