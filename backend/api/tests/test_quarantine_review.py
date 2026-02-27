"""Tests for T14: Quarantine review workflow."""
from datetime import timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import Bus, Office, QuarantinedSync, Trip, User


class QuarantineReviewTests(TestCase):
    """Quarantine list, single review, bulk review, and office scoping."""

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
        bus = Bus.objects.create(
            plate_number='BUS-01', capacity=50, office=self.office,
        )
        self.trip = Trip.objects.create(
            origin_office=self.office,
            destination_office=self.office_b,
            conductor=self.conductor, bus=bus,
            departure_datetime=timezone.now() + timedelta(hours=1),
            status='in_progress',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )
        self.qs1 = QuarantinedSync.objects.create(
            conductor=self.conductor, trip=self.trip,
            original_data={
                'type': 'passenger_ticket',
                'payload': {'passenger_name': 'Test'},
                'idempotency_key': 'qs1-key',
            },
            reason='Bus at full capacity',
        )
        self.qs2 = QuarantinedSync.objects.create(
            conductor=self.conductor, trip=self.trip,
            original_data={
                'type': 'expense',
                'payload': {'amount': -10},
                'idempotency_key': 'qs2-key',
            },
            reason='Invalid amount',
        )
        self.client = APIClient()

    def test_list_quarantine(self):
        """Admin can list all quarantined items."""
        self.client.force_authenticate(self.admin)
        resp = self.client.get('/api/quarantine/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(len(resp.data['results']), 2)

    def test_single_reject(self):
        """Reject a single quarantined item."""
        self.client.force_authenticate(self.admin)
        resp = self.client.post(f'/api/quarantine/{self.qs1.id}/review/', {
            'status': 'rejected',
            'review_notes': 'Invalid data',
        })
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.qs1.refresh_from_db()
        self.assertEqual(self.qs1.status, 'rejected')
        self.assertIsNotNone(self.qs1.reviewed_at)

    def test_single_approve_reprocesses(self):
        """Approved item is re-processed through sync pipeline."""
        self.qs1.original_data = {
            'type': 'passenger_ticket',
            'payload': {'passenger_name': 'Reprocessed'},
            'idempotency_key': 'qs1-key',
        }
        self.qs1.save()
        self.client.force_authenticate(self.admin)
        resp = self.client.post(f'/api/quarantine/{self.qs1.id}/review/', {
            'status': 'approved',
        })
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.qs1.refresh_from_db()
        self.assertEqual(self.qs1.status, 'approved')

    def test_bulk_reject(self):
        """Bulk reject multiple items."""
        self.client.force_authenticate(self.admin)
        resp = self.client.post('/api/quarantine/bulk_review/', {
            'ids': [self.qs1.id, self.qs2.id],
            'action': 'reject',
            'review_notes': 'Batch rejected',
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data['processed'], 2)

    def test_staff_sees_own_office_only(self):
        """Staff only see quarantine items for their office's trips."""
        self.client.force_authenticate(self.staff)
        resp = self.client.get('/api/quarantine/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(len(resp.data['results']), 2)

    def test_conductor_cannot_review(self):
        """Conductors don't have review access."""
        self.client.force_authenticate(self.conductor)
        resp = self.client.get('/api/quarantine/')
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)
