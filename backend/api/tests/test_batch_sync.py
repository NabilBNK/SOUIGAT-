"""Tests for T13: Batch sync endpoint."""
import hashlib
import json
from datetime import date, timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import (
    Bus, CargoTicket, Office, PassengerTicket,
    PricingConfig, QuarantinedSync, SyncLog, Trip, TripExpense, User,
)


class BatchSyncTests(TestCase):
    """Batch sync idempotency, validation, quarantine, and resume."""

    def setUp(self):
        self.office = Office.objects.create(name='HQ', city='Algiers')
        self.office_b = Office.objects.create(name='Branch', city='Oran')
        self.conductor = User.objects.create_user(
            phone='0550000001', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office,
        )
        self.staff = User.objects.create_user(
            phone='0550000002', password='pass',
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

    def _make_item(self, item_type='passenger_ticket', key=None, payload=None):
        """Helper to create a sync item."""
        if key is None:
            key = hashlib.sha256(json.dumps(payload or {}).encode()).hexdigest()
        if payload is None:
            payload = {'passenger_name': 'Test', 'payment_source': 'cash'}
        return {'type': item_type, 'idempotency_key': key, 'payload': payload}

    def test_sync_accepts_valid_items(self):
        """Valid passenger ticket is accepted."""
        self.client.force_authenticate(self.conductor)
        item = self._make_item(key='abc123')
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [item],
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['accepted'], 1)
        self.assertEqual(resp.data['quarantined'], 0)
        self.assertEqual(PassengerTicket.objects.count(), 1)

    def test_sync_idempotent_skip(self):
        """Duplicate idempotency key is skipped."""
        SyncLog.objects.create(
            key='dup-key', conductor=self.conductor, trip=self.trip,
        )
        self.client.force_authenticate(self.conductor)
        item = self._make_item(key='dup-key')
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [item],
        }, format='json')
        self.assertEqual(resp.data['duplicates'], 1)
        self.assertEqual(resp.data['accepted'], 0)

    def test_sync_quarantines_invalid(self):
        """Invalid item is quarantined, not rejected."""
        self.client.force_authenticate(self.conductor)
        item = self._make_item(
            item_type='expense', key='exp-bad',
            payload={'description': 'Fuel', 'amount': -100},
        )
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [item],
        }, format='json')
        self.assertEqual(resp.data['quarantined'], 1)
        self.assertEqual(QuarantinedSync.objects.count(), 1)

    def test_sync_mixed_batch(self):
        """Batch with valid + invalid + duplicate items."""
        SyncLog.objects.create(
            key='dup-key', conductor=self.conductor, trip=self.trip,
        )
        self.client.force_authenticate(self.conductor)
        items = [
            self._make_item(key='valid-1'),
            self._make_item(item_type='expense', key='bad-1', payload={'amount': -1}),
            self._make_item(key='dup-key'),
        ]
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': items,
        }, format='json')
        self.assertEqual(resp.data['accepted'], 1)
        self.assertEqual(resp.data['quarantined'], 1)
        self.assertEqual(resp.data['duplicates'], 1)

    def test_sync_resume_from_index(self):
        """Resume skips items before resume_from."""
        self.client.force_authenticate(self.conductor)
        items = [
            self._make_item(key='skip-0'),
            self._make_item(key='skip-1'),
            self._make_item(key='accept-2'),
        ]
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': items,
            'resume_from': 2,
        }, format='json')
        self.assertEqual(resp.data['accepted'], 1)
        self.assertEqual(len(resp.data['items']), 1)

    def test_sync_conductor_only(self):
        """Office staff cannot sync."""
        self.client.force_authenticate(self.staff)
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [self._make_item(key='x')],
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)

    def test_sync_wrong_conductor(self):
        """Conductor cannot sync another conductor's trip."""
        other = User.objects.create_user(
            phone='0550000099', password='pass',
            first_name='Other', last_name='Cond',
            role='conductor', office=self.office,
        )
        self.client.force_authenticate(other)
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [self._make_item(key='y')],
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)

    # --- Regression tests for Week 4 audit bugs ---

    def test_ticket_number_no_collision_after_cancel(self):
        """Bug #1: Ticket number uses total count, not active count."""
        self.client.force_authenticate(self.conductor)
        # Create 3 tickets
        for i in range(3):
            self.client.post('/api/sync/batch/', {
                'trip_id': self.trip.id,
                'items': [self._make_item(key=f'ticket-{i}')],
            }, format='json')
        # Cancel one
        ticket = PassengerTicket.objects.first()
        ticket.status = 'cancelled'
        ticket.save()
        # Create another — should NOT collide
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [self._make_item(key='ticket-after-cancel')],
        }, format='json')
        self.assertEqual(resp.data['accepted'], 1)
        # Verify unique ticket numbers
        numbers = list(PassengerTicket.objects.values_list('ticket_number', flat=True))
        self.assertEqual(len(numbers), len(set(numbers)), f'Duplicate ticket numbers: {numbers}')

    def test_invalid_cargo_tier_rejected(self):
        """Bug #3: Invalid cargo_tier raises error instead of price=0."""
        self.client.force_authenticate(self.conductor)
        item = self._make_item(
            item_type='cargo_ticket', key='bad-tier',
            payload={'cargo_tier': 'HUGE', 'sender_name': 'A', 'receiver_name': 'B'},
        )
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [item],
        }, format='json')
        self.assertEqual(resp.data['quarantined'], 1)
        self.assertEqual(CargoTicket.objects.count(), 0)

    def test_invalid_expense_amount_quarantined(self):
        """Bug #4: Non-numeric expense amount is quarantined, not 500 error."""
        self.client.force_authenticate(self.conductor)
        item = self._make_item(
            item_type='expense', key='bad-amount',
            payload={'description': 'Fuel', 'amount': 'abc'},
        )
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [item],
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['quarantined'], 1)

    def test_resume_from_exceeds_batch_rejected(self):
        """Bug #6: resume_from >= len(items) rejected by serializer."""
        self.client.force_authenticate(self.conductor)
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [self._make_item(key='only-one')],
            'resume_from': 5,
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)

    def test_last_processed_index_includes_duplicates(self):
        """Bug #7: last_processed_index tracks accepted + duplicate items."""
        SyncLog.objects.create(
            key='dup-at-1', conductor=self.conductor, trip=self.trip,
        )
        self.client.force_authenticate(self.conductor)
        items = [
            self._make_item(key='accept-0'),
            self._make_item(key='dup-at-1'),
        ]
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': items,
        }, format='json')
        self.assertEqual(resp.data['last_processed_index'], 1)

