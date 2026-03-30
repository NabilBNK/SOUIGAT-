"""Tests for T15: Report endpoints."""
from datetime import timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import (
    Bus, CargoTicket, Office, PassengerTicket,
    PricingConfig, Settlement, Trip, TripExpense, User,
)
from api.services.report_snapshots import upsert_trip_report_snapshots_for_trip


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
            status='completed',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )
        self.settlement = Settlement.objects.create(
            trip=self.trip,
            office=self.office_b,
            conductor=self.conductor,
            expected_passenger_cash=2000,
            expected_cargo_cash=500,
            expected_total_cash=2500,
            expenses_to_reimburse=300,
            net_cash_expected=2200,
            status=Settlement.STATUS_SETTLED,
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
        """Admin all-offices daily report counts each trip once globally."""
        self.client.force_authenticate(self.admin)
        today = timezone.now().strftime('%Y-%m-%d')
        resp = self.client.get(f'/api/reports/daily/?date_from={today}&date_to={today}')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(type(resp.data), list)
        self.assertEqual(len(resp.data), 1)

        global_row = resp.data[0]
        self.assertEqual(global_row['office_id'], 0)
        self.assertEqual(global_row['office_name'], 'Toutes les agences')
        self.assertEqual(global_row['total_trips'], 1)
        self.assertEqual(global_row['passenger_revenue'], 2000)
        self.assertEqual(global_row['cargo_revenue'], 500)
        self.assertEqual(global_row['expense_total'], 300)
        self.assertEqual(global_row['net_revenue'], 2200)
        self.assertEqual(global_row['total_passengers'], 2)
        
    def test_daily_report_office_filter(self):
        """Admin can filter daily report by office_id."""
        self.client.force_authenticate(self.admin)
        today = timezone.now().strftime('%Y-%m-%d')
        resp = self.client.get(f'/api/reports/daily/?date_from={today}&date_to={today}&office_id={self.office_b.id}')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        # Should only return one row for office_b
        self.assertEqual(len(resp.data), 1)
        self.assertEqual(resp.data[0]['office_id'], self.office_b.id)
        # Trip origin is Algiers, destination is Oran, so Oran sees the trip
        self.assertEqual(resp.data[0]['total_trips'], 1)

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

    def test_staff_denied_reports(self):
        """Office staff are denied report endpoints."""
        self.client.force_authenticate(self.staff)
        today = timezone.now().strftime('%Y-%m-%d')
        resp = self.client.get(f'/api/reports/daily/?date_from={today}&date_to={today}')
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)

    def test_conductor_cannot_access_reports(self):
        """Conductors are denied access to reports."""
        self.client.force_authenticate(self.conductor)
        resp = self.client.get('/api/reports/daily/')
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)
        
    def test_conductor_report(self):
        """Admin gets conductor performance data."""
        self.client.force_authenticate(self.admin)
        today = timezone.now().strftime('%Y-%m-%d')
        resp = self.client.get(f'/api/reports/conductors/?date_from={today}&date_to={today}')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(len(resp.data), 1)
        self.assertEqual(resp.data[0]['conductor_id'], self.conductor.id)
        self.assertEqual(resp.data[0]['total_trips'], 1)
        self.assertEqual(resp.data[0]['total_revenue'], 2500)
        self.assertEqual(resp.data[0]['total_expenses'], 300)
        self.assertEqual(resp.data[0]['net'], 2200)

    def test_conductor_report_non_admin(self):
        """Office staff are denied access to conductor reports."""
        self.client.force_authenticate(self.staff)
        resp = self.client.get('/api/reports/conductors/')
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)

    def test_invalid_date_format_returns_400(self):
        self.client.force_authenticate(self.admin)
        resp = self.client.get('/api/reports/daily/?date_from=2026-99-99')
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertEqual(resp.data['error_code'], 'INVALID_DATE_RANGE')

    def test_excessive_date_range_rejected(self):
        self.client.force_authenticate(self.admin)
        resp = self.client.get('/api/reports/daily/?date_from=2026-01-01&date_to=2026-02-15')
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertEqual(resp.data['error_code'], 'DATE_RANGE_TOO_LARGE')

    def test_daily_report_excludes_unsettled_trip(self):
        self.settlement.status = Settlement.STATUS_PENDING
        self.settlement.save(update_fields=['status', 'updated_at'])

        self.client.force_authenticate(self.admin)
        today = timezone.now().strftime('%Y-%m-%d')
        resp = self.client.get(f'/api/reports/daily/?date_from={today}&date_to={today}')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(len(resp.data), 1)
        self.assertEqual(resp.data[0]['total_trips'], 0)

    def test_daily_report_prefers_settled_snapshot(self):
        self.trip.status = 'completed'
        self.trip.save()

        settlement = self.settlement
        upsert_trip_report_snapshots_for_trip(self.trip)

        PassengerTicket.objects.filter(trip=self.trip).update(status='cancelled')
        settlement.notes = 'snapshot is frozen'
        settlement.save(update_fields=['notes', 'updated_at'])

        self.client.force_authenticate(self.admin)
        today = timezone.now().strftime('%Y-%m-%d')
        resp = self.client.get(f'/api/reports/daily/?date_from={today}&date_to={today}')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)

        self.assertEqual(len(resp.data), 1)
        global_row = resp.data[0]
        self.assertEqual(global_row['office_id'], 0)
        self.assertEqual(global_row['passenger_revenue'], 2000)
        self.assertEqual(global_row['cargo_revenue'], 500)
        self.assertEqual(global_row['expense_total'], 300)
        self.assertEqual(global_row['net_revenue'], 2200)
