"""Tests for T13: Batch sync endpoint."""
import hashlib
import json
from datetime import date, timedelta
from unittest.mock import patch

from django.db import connection
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

    def test_sync_allows_boarding_after_intermediate_dropoff(self):
        small_bus = Bus.objects.create(
            plate_number='BUS-INT-01', capacity=2, office=self.office,
        )
        intermediate_trip = Trip.objects.create(
            origin_office=self.office,
            destination_office=self.office_b,
            conductor=self.conductor,
            bus=small_bus,
            departure_datetime=timezone.now() + timedelta(hours=2),
            status='in_progress',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )
        PassengerTicket.objects.create(
            trip=intermediate_trip,
            ticket_number='PT-INT-001',
            passenger_name='Leaves early',
            price=700,
            currency='DZD',
            payment_source='cash',
            boarding_point=self.office.name,
            alighting_point='Ghardaia',
            created_by=self.conductor,
        )
        PassengerTicket.objects.create(
            trip=intermediate_trip,
            ticket_number='PT-INT-002',
            passenger_name='Stays onboard',
            price=1000,
            currency='DZD',
            payment_source='cash',
            boarding_point=self.office.name,
            alighting_point=self.office_b.name,
            created_by=self.conductor,
        )

        self.client.force_authenticate(self.conductor)
        item = self._make_item(
            key='int-reboard-1',
            payload={
                'passenger_name': 'Boards at Ghardaia',
                'payment_source': 'cash',
                'boarding_point': 'Ghardaia',
                'alighting_point': self.office_b.name,
                'price': 600,
            },
        )
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': intermediate_trip.id,
            'items': [item],
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['accepted'], 1)
        self.assertEqual(resp.data['quarantined'], 0)

    def test_sync_keeps_manual_price_for_full_route_ticket(self):
        self.client.force_authenticate(self.conductor)
        item = self._make_item(
            key='manual-price-full-route',
            payload={
                'passenger_name': 'Tarif manuel',
                'payment_source': 'cash',
                'price': 750,
            },
        )
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [item],
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['accepted'], 1)
        ticket = PassengerTicket.objects.get(passenger_name='Tarif manuel')
        self.assertEqual(ticket.price, 750)

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

    def test_sync_normalizes_legacy_mobile_passenger_price(self):
        self.client.force_authenticate(self.conductor)
        item = self._make_item(
            key='legacy-passenger-price',
            payload={
                'passenger_name': 'Legacy Mobile',
                'payment_source': 'cash',
                'price': 100000,
            },
        )
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [item],
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        ticket = PassengerTicket.objects.get(passenger_name='Legacy Mobile')
        self.assertEqual(ticket.price, 1000)
        self.assertIsNotNone(ticket.synced_at)

    def test_sync_normalizes_legacy_mobile_expense_amount(self):
        self.client.force_authenticate(self.conductor)
        item = self._make_item(
            item_type='expense',
            key='legacy-expense',
            payload={'description': '', 'amount': 380500},
        )
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [item],
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        expense = TripExpense.objects.get(trip=self.trip)
        self.assertEqual(expense.amount, 3805)
        self.assertIsNotNone(expense.synced_at)

    def test_sync_persists_expense_category(self):
        self.client.force_authenticate(self.conductor)
        item = self._make_item(
            item_type='expense',
            key='expense-category',
            payload={
                'description': 'Fuel stop',
                'amount': 900,
                'category': 'fuel',
            },
        )
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [item],
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        expense = TripExpense.objects.get(trip=self.trip)
        self.assertEqual(expense.category, 'fuel')

    def test_sync_invalid_expense_category_quarantined(self):
        self.client.force_authenticate(self.conductor)
        item = self._make_item(
            item_type='expense',
            key='bad-expense-category',
            payload={
                'description': 'Unknown',
                'amount': 900,
                'category': 'bribe',
            },
        )
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [item],
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['quarantined'], 1)

    def test_sync_respects_explicit_base_unit_money_scale(self):
        self.client.force_authenticate(self.conductor)
        item = self._make_item(
            item_type='expense',
            key='base-unit-expense',
            payload={
                'description': '',
                'amount': 100900,
                'money_scale': 'base_unit',
            },
        )
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [item],
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        expense = TripExpense.objects.get(trip=self.trip)
        self.assertEqual(expense.amount, 100900)

    def test_resume_from_exceeds_batch_size_rejected_by_serializer(self):
        """Serializer validation — not view-level clamp."""
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

    def test_local_id_echoed_in_accepted(self):
        """FIX 1A: local_id from mobile is echoed back in accepted items."""
        self.client.force_authenticate(self.conductor)
        item = self._make_item(key='with-local-id')
        item['local_id'] = 42  # Room entity PK
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [item],
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['accepted'], 1)
        accepted_item = resp.data['items'][0]
        self.assertEqual(accepted_item['local_id'], 42)

    def test_local_id_absent_when_not_sent(self):
        """local_id is omitted from response when not provided in request."""
        self.client.force_authenticate(self.conductor)
        item = self._make_item(key='no-local-id')
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [item],
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        accepted_item = resp.data['items'][0]
        self.assertNotIn('local_id', accepted_item)


    def test_local_id_echoed_in_quarantined(self):
        """local_id is echoed in quarantined items (e.g. invalid expense amount)."""
        self.client.force_authenticate(self.conductor)
        item = self._make_item(
            item_type='expense', key='quarantine-with-local-id',
            payload={'description': 'Fuel', 'amount': -1},
        )
        item['local_id'] = 99  # Room entity PK
        resp = self.client.post('/api/sync/batch/', {
            'trip_id': self.trip.id,
            'items': [item],
        }, format='json')
        self.assertEqual(resp.data['quarantined'], 1)
        quarantined_item = resp.data['items'][0]
        self.assertEqual(quarantined_item['status'], 'quarantined')
        self.assertEqual(quarantined_item['local_id'], 99)

    def test_database_error_on_one_item_does_not_break_remaining_batch(self):
        """A DB error rolls back only the failed item and preserves the rest of the batch."""
        self.client.force_authenticate(self.conductor)

        def broken_expense(*args, **kwargs):
            with connection.cursor() as cursor:
                cursor.execute('SELECT * FROM definitely_missing_table')

        items = [
            self._make_item(
                item_type='expense',
                key='db-error-expense',
                payload={'description': 'Fuel', 'amount': 500},
            ),
            self._make_item(key='after-db-error'),
        ]

        with patch('api.views.sync_views._create_expense', side_effect=broken_expense):
            resp = self.client.post('/api/sync/batch/', {
                'trip_id': self.trip.id,
                'items': items,
            }, format='json')

        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['quarantined'], 1)
        self.assertEqual(resp.data['accepted'], 1)
        self.assertEqual(QuarantinedSync.objects.count(), 1)
        self.assertEqual(PassengerTicket.objects.count(), 1)
