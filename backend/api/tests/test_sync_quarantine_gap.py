"""Tests for Bug #1 (sync quarantine gap) and Bug #13 (quarantine reprocess)."""
from datetime import date, timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import (
    Bus, Office, PassengerTicket, PricingConfig,
    QuarantinedSync, SyncLog, Trip, User,
)


class SyncQuarantineGapTests(TestCase):
    """Bug #1: completed/cancelled trip sync should quarantine, not reject."""

    def setUp(self):
        self.office = Office.objects.create(name='HQ', city='Algiers')
        self.office_b = Office.objects.create(name='Branch', city='Oran')
        self.conductor = User.objects.create_user(
            phone='0550000001', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office,
        )
        self.admin = User.objects.create_superuser(
            phone='0550000099', password='pass',
            first_name='Admin', last_name='X',
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
            departure_datetime=timezone.now() - timedelta(hours=2),
            status='completed',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )
        self.client = APIClient()

    def test_completed_trip_sync_quarantines_not_rejects(self):
        """Sync to completed trip returns 200 with quarantined items, NOT 400."""
        self.client.force_authenticate(self.conductor)
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [{
                'type': 'passenger_ticket',
                'idempotency_key': 'late-sync-1',
                'payload': {'passenger_name': 'Late Passenger', 'payment_source': 'cash'},
            }],
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['quarantined'], 1)
        self.assertEqual(resp.data['accepted'], 0)
        self.assertEqual(QuarantinedSync.objects.count(), 1)

    def test_cancelled_trip_sync_quarantines(self):
        """Sync to cancelled trip quarantines items."""
        self.trip.status = 'cancelled'
        self.trip.save()
        self.client.force_authenticate(self.conductor)
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [{
                'type': 'passenger_ticket',
                'idempotency_key': 'cancelled-sync-1',
                'payload': {'passenger_name': 'Orphan', 'payment_source': 'cash'},
            }],
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['quarantined'], 1)

    def test_quarantine_idempotency_on_closed_trip(self):
        """Duplicate keys still skipped on closed trips."""
        SyncLog.objects.create(
            key='already-done', conductor=self.conductor, trip=self.trip,
        )
        self.client.force_authenticate(self.conductor)
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [{
                'type': 'passenger_ticket',
                'idempotency_key': 'already-done',
                'payload': {'passenger_name': 'Dup', 'payment_source': 'cash'},
            }],
        }, format='json')
        self.assertEqual(resp.data['duplicates'], 1)
        self.assertEqual(resp.data['quarantined'], 0)

    def test_quarantine_approval_works_on_completed_trip(self):
        """Bug #13: Admin can approve quarantined items for completed trips."""
        # First, create a quarantined item
        self.client.force_authenticate(self.conductor)
        self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [{
                'type': 'passenger_ticket',
                'idempotency_key': 'late-approval-1',
                'payload': {'passenger_name': 'Late', 'payment_source': 'cash'},
            }],
        }, format='json')

        qs_item = QuarantinedSync.objects.first()
        self.assertIsNotNone(qs_item)

        # Admin approves the quarantined item
        self.client.force_authenticate(self.admin)
        resp = self.client.post(
            f'/api/quarantine/{qs_item.id}/review/',
            {'status': 'approved'},
            format='json',
        )
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        # Ticket should now exist
        self.assertTrue(
            PassengerTicket.objects.filter(trip=self.trip).exists()
        )
