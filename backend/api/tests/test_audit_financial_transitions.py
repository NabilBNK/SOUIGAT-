"""Tests for audit old_values capture on financial state transitions.

Verifies that deliver, complete_trip, and quarantine_approve all
produce audit log entries with action='update' and non-null old_values.
"""
from datetime import date, timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import (
    AuditLog, Bus, CargoTicket, Office,
    PricingConfig, QuarantinedSync, Trip, User,
)


class DeliverAuditTests(TestCase):
    """Cargo deliver should capture old_values in audit log."""

    def setUp(self):
        self.office_a = Office.objects.create(name='Algiers', city='Algiers')
        self.office_b = Office.objects.create(name='Oran', city='Oran')
        self.staff_b = User.objects.create_user(
            phone='0550000010', password='pass',
            first_name='Staff', last_name='B',
            role='office_staff', office=self.office_b,
        )
        self.conductor = User.objects.create_user(
            phone='0550000011', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office_a,
        )
        bus = Bus.objects.create(
            plate_number='BUS-AUDIT-1', capacity=50, office=self.office_a,
        )
        PricingConfig.objects.create(
            origin_office=self.office_a, destination_office=self.office_b,
            passenger_price=1000, cargo_small_price=500,
            cargo_medium_price=1000, cargo_large_price=1500,
            effective_from=date.today(),
        )
        self.trip = Trip.objects.create(
            origin_office=self.office_a, destination_office=self.office_b,
            conductor=self.conductor, bus=bus,
            departure_datetime=timezone.now() + timedelta(hours=1),
            status='in_progress',
            passenger_base_price=1000,
            cargo_small_price=500, cargo_medium_price=1000, cargo_large_price=1500,
        )
        self.cargo = CargoTicket.objects.create(
            trip=self.trip, ticket_number='CT-AUDIT-001',
            sender_name='S', receiver_name='R',
            cargo_tier='medium', price=1000,
            payment_source='prepaid', created_by=self.staff_b,
            status='arrived',
        )
        self.client = APIClient()

    def test_deliver_captures_old_values(self):
        """Deliver action creates audit log with action='update' and old_values."""
        self.client.force_authenticate(self.staff_b)
        resp = self.client.post(f'/api/cargo/{self.cargo.id}/deliver/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)

        log = AuditLog.objects.filter(
            table_name='cargo', record_id=self.cargo.id
        ).order_by('-id').first()
        self.assertIsNotNone(log, 'No audit log found for deliver')
        self.assertEqual(
            log.action, 'update',
            'Deliver should be an update. If this fails as "create", '
            'the AuditMiddleware dynamic state snapshot on POST actions may have failed.'
        )
        self.assertIsNotNone(log.old_values, 'old_values should be populated for deliver')
        self.assertEqual(log.old_values['status'], 'arrived')


class CompleteTripAuditTests(TestCase):
    """Trip complete should capture old_values in audit log."""

    def setUp(self):
        self.office_a = Office.objects.create(name='Algiers', city='Algiers')
        self.office_b = Office.objects.create(name='Oran', city='Oran')
        self.conductor = User.objects.create_user(
            phone='0550000020', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office_a,
        )
        bus = Bus.objects.create(
            plate_number='BUS-AUDIT-2', capacity=50, office=self.office_a,
        )
        PricingConfig.objects.create(
            origin_office=self.office_a, destination_office=self.office_b,
            passenger_price=1000, cargo_small_price=500,
            cargo_medium_price=1000, cargo_large_price=1500,
            effective_from=date.today(),
        )
        self.trip = Trip.objects.create(
            origin_office=self.office_a, destination_office=self.office_b,
            conductor=self.conductor, bus=bus,
            departure_datetime=timezone.now() - timedelta(hours=2),
            status='in_progress',
            passenger_base_price=1000,
            cargo_small_price=500, cargo_medium_price=1000, cargo_large_price=1500,
        )
        self.client = APIClient()

    def test_complete_trip_captures_old_values(self):
        """Complete action creates audit log with action='update' and old_values."""
        self.client.force_authenticate(self.conductor)
        resp = self.client.post(f'/api/trips/{self.trip.id}/complete/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)

        log = AuditLog.objects.filter(
            table_name='trips', record_id=self.trip.id
        ).order_by('-id').first()
        self.assertIsNotNone(log, 'No audit log found for complete')
        self.assertEqual(
            log.action, 'update',
            'Complete should be an update. If this fails as "create", '
            'the AuditMiddleware dynamic state snapshot on POST actions may have failed.'
        )
        self.assertIsNotNone(log.old_values, 'old_values should be populated for complete')
        self.assertEqual(log.old_values['status'], 'in_progress')

    def test_start_trip_captures_old_values(self):
        """Start action creates audit log with old status."""
        conductor2 = User.objects.create_user(
            phone='0550000021', password='pass',
            first_name='Cond', last_name='B',
            role='conductor', office=self.office_a,
        )
        trip = Trip.objects.create(
            origin_office=self.office_a, destination_office=self.office_b,
            conductor=conductor2,
            bus=Bus.objects.create(
                plate_number='BUS-AUDIT-3', capacity=50, office=self.office_a,
            ),
            departure_datetime=timezone.now() + timedelta(hours=1),
            status='scheduled',
            passenger_base_price=1000,
            cargo_small_price=500, cargo_medium_price=1000, cargo_large_price=1500,
        )
        self.client.force_authenticate(conductor2)
        resp = self.client.post(f'/api/trips/{trip.id}/start/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)

        log = AuditLog.objects.filter(
            table_name='trips', record_id=trip.id
        ).order_by('-id').first()
        self.assertIsNotNone(log, 'No audit log found for start')
        self.assertEqual(
            log.action, 'update',
            'Start should be an update. If this fails as "create", '
            'the AuditMiddleware dynamic state snapshot on POST actions may have failed.'
        )
        self.assertIsNotNone(log.old_values, 'old_values should be populated for start')
        self.assertEqual(log.old_values['status'], 'scheduled')


class QuarantineReviewAuditTests(TestCase):
    """Quarantine review should capture old_values in audit log."""

    def setUp(self):
        self.office = Office.objects.create(name='Algiers', city='Algiers')
        self.office_b = Office.objects.create(name='Oran', city='Oran')
        self.admin = User.objects.create_superuser(
            phone='0550000030', password='pass',
            first_name='Admin', last_name='X',
        )
        self.conductor = User.objects.create_user(
            phone='0550000031', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office,
        )
        bus = Bus.objects.create(
            plate_number='BUS-AUDIT-4', capacity=50, office=self.office,
        )
        PricingConfig.objects.create(
            origin_office=self.office, destination_office=self.office_b,
            passenger_price=1000, cargo_small_price=500,
            cargo_medium_price=1000, cargo_large_price=1500,
            effective_from=date.today(),
        )
        self.trip = Trip.objects.create(
            origin_office=self.office, destination_office=self.office_b,
            conductor=self.conductor, bus=bus,
            departure_datetime=timezone.now() + timedelta(hours=1),
            status='completed',
            passenger_base_price=1000,
            cargo_small_price=500, cargo_medium_price=1000, cargo_large_price=1500,
        )
        self.qs_item = QuarantinedSync.objects.create(
            conductor=self.conductor, trip=self.trip,
            original_data={
                'type': 'passenger_ticket',
                'payload': {'passenger_name': 'Test'},
                'idempotency_key': 'audit-test-key',
            },
            reason='Test quarantine',
        )
        self.client = APIClient()

    def test_quarantine_review_captures_old_values(self):
        """Review (reject) creates audit log with action='update' and old_values."""
        self.client.force_authenticate(self.admin)
        resp = self.client.post(
            f'/api/quarantine/{self.qs_item.id}/review/',
            {'status': 'rejected', 'review_notes': 'Testing audit'},
        )
        self.assertEqual(resp.status_code, status.HTTP_200_OK)

        log = AuditLog.objects.filter(
            table_name='quarantine', record_id=self.qs_item.id
        ).order_by('-id').first()
        self.assertIsNotNone(log, 'No audit log found for review')
        self.assertEqual(
            log.action, 'update',
            'Review should be an update. If this fails as "create", '
            'the AuditMiddleware dynamic state snapshot on POST actions may have failed.'
        )
        self.assertIsNotNone(log.old_values, 'old_values should be populated for review')
        self.assertEqual(log.old_values['status'], 'pending')
