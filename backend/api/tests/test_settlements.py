from datetime import timedelta
from unittest.mock import patch

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import (
    AuditLog,
    Bus,
    CargoTicket,
    Office,
    PassengerTicket,
    QuarantinedSync,
    Settlement,
    Trip,
    TripExpense,
    User,
)
from api.services import SettlementComputation, compute_settlement, initiate_settlement_for_trip


class SettlementWorkflowTests(TestCase):
    def setUp(self):
        self.origin = Office.objects.create(name='Ouargla', city='Ouargla')
        self.destination = Office.objects.create(name='Algiers', city='Algiers')
        self.other_destination = Office.objects.create(name='Oran', city='Oran')

        self.admin = User.objects.create_user(
            phone='0551000001',
            password='pass',
            first_name='Admin',
            last_name='User',
            role='admin',
        )
        self.destination_staff = User.objects.create_user(
            phone='0551000002',
            password='pass',
            first_name='Desk',
            last_name='Algiers',
            role='office_staff',
            department='all',
            office=self.destination,
        )
        self.other_staff = User.objects.create_user(
            phone='0551000003',
            password='pass',
            first_name='Desk',
            last_name='Oran',
            role='office_staff',
            department='all',
            office=self.other_destination,
        )
        self.conductor = User.objects.create_user(
            phone='0551000004',
            password='pass',
            first_name='Cond',
            last_name='One',
            role='conductor',
            office=self.origin,
        )

        self.bus = Bus.objects.create(
            plate_number='SET-001',
            capacity=52,
            office=self.origin,
        )
        self.other_bus = Bus.objects.create(
            plate_number='SET-002',
            capacity=48,
            office=self.origin,
        )
        self.client = APIClient()

    def _make_trip(self, **overrides):
        defaults = {
            'origin_office': self.origin,
            'destination_office': self.destination,
            'conductor': self.conductor,
            'bus': self.bus,
            'departure_datetime': timezone.now() - timedelta(hours=3),
            'arrival_datetime': timezone.now() - timedelta(hours=1),
            'status': 'completed',
            'passenger_base_price': 1000,
            'cargo_small_price': 500,
            'cargo_medium_price': 1000,
            'cargo_large_price': 1500,
        }
        defaults.update(overrides)
        return Trip.objects.create(**defaults)

    def test_trip_expense_synced_at_is_set_during_batch_sync(self):
        trip = self._make_trip(
            status='in_progress',
            departure_datetime=timezone.now() - timedelta(hours=1),
            arrival_datetime=None,
        )
        self.client.force_authenticate(self.conductor)

        response = self.client.post(
            '/api/sync/batch/',
            {
                'trip_id': trip.id,
                'items': [
                    {
                        'type': 'expense',
                        'idempotency_key': 'exp-sync-1',
                        'payload': {'description': 'Fuel', 'amount': 350},
                    }
                ],
            },
            format='json',
        )

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        expense = TripExpense.objects.get(trip=trip)
        self.assertIsNotNone(expense.synced_at)

    def test_force_complete_blocks_unsynced_expenses(self):
        trip = self._make_trip(
            status='in_progress',
            departure_datetime=timezone.now() - timedelta(hours=1),
            arrival_datetime=None,
        )
        TripExpense.objects.create(
            trip=trip,
            description='Fuel',
            amount=400,
            currency='DZD',
            created_by=self.conductor,
            synced_at=None,
        )
        self.client.force_authenticate(self.admin)

        response = self.client.post(
            f'/api/trips/{trip.id}/force_complete/',
            {'force_reason': 'Conductor phone unavailable'},
            format='json',
        )

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertEqual(response.data['error_code'], 'TRIP_HAS_PENDING_SYNC')
        trip.refresh_from_db()
        self.assertEqual(trip.status, 'in_progress')

    def test_complete_auto_creates_settlement_preview(self):
        trip = self._make_trip(
            status='in_progress',
            departure_datetime=timezone.now() - timedelta(hours=2),
            arrival_datetime=None,
        )
        PassengerTicket.objects.create(
            trip=trip,
            ticket_number='PT-001',
            passenger_name='Cash Rider',
            price=1200,
            currency='DZD',
            payment_source='cash',
            created_by=self.conductor,
            synced_at=timezone.now(),
        )

        self.client.force_authenticate(self.conductor)
        response = self.client.post(f'/api/trips/{trip.id}/complete/')

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['status'], 'completed')
        self.assertIsNone(response.data['settlement_preview_error'])
        self.assertEqual(response.data['settlement_preview']['expected_total_cash'], 1200)
        settlement = Settlement.objects.get(trip=trip)
        self.assertEqual(settlement.office_id, self.destination.id)
        self.assertEqual(settlement.expected_total_cash, 1200)

    @patch('api.views.trip_views.initiate_settlement_for_trip', side_effect=Exception('boom'))
    def test_complete_soft_fails_when_settlement_init_errors(self, mocked_initiate):
        trip = self._make_trip(
            status='in_progress',
            departure_datetime=timezone.now() - timedelta(hours=2),
            arrival_datetime=None,
        )
        self.client.force_authenticate(self.conductor)

        response = self.client.post(f'/api/trips/{trip.id}/complete/')

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIsNone(response.data['settlement_preview'])
        self.assertEqual(response.data['settlement_preview_error'], 'settlement_init_failed')
        trip.refresh_from_db()
        self.assertEqual(trip.status, 'completed')
        self.assertFalse(Settlement.objects.filter(trip=trip).exists())
        self.assertEqual(mocked_initiate.call_count, 1)

    @patch('api.views.trip_views.initiate_settlement_for_trip', side_effect=Exception('boom'))
    def test_force_complete_rolls_back_when_settlement_init_errors(self, mocked_initiate):
        trip = self._make_trip(
            status='in_progress',
            departure_datetime=timezone.now() - timedelta(hours=2),
            arrival_datetime=None,
        )
        self.client.force_authenticate(self.admin)

        response = self.client.post(
            f'/api/trips/{trip.id}/force_complete/',
            {'force_reason': 'Desk override'},
            format='json',
        )

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertEqual(response.data['error_code'], 'SETTLEMENT_INIT_FAILED')
        trip.refresh_from_db()
        self.assertEqual(trip.status, 'in_progress')
        self.assertFalse(Settlement.objects.filter(trip=trip).exists())
        self.assertEqual(mocked_initiate.call_count, 1)

    def test_initiate_service_recovers_from_integrity_error(self):
        trip = self._make_trip()
        existing = Settlement.objects.create(
            trip=trip,
            office=trip.destination_office,
            conductor=trip.conductor,
            expected_passenger_cash=0,
            expected_cargo_cash=0,
            expected_total_cash=0,
            agency_presale_total=0,
            outstanding_cargo_delivery=0,
            expenses_to_reimburse=0,
            net_cash_expected=0,
            calculation_snapshot={},
        )

        with patch('api.services.settlements.Settlement.objects.get_or_create') as mocked_get_or_create:
            from django.db import IntegrityError

            mocked_get_or_create.side_effect = IntegrityError('duplicate key')
            settlement, created = initiate_settlement_for_trip(trip)

        self.assertFalse(created)
        self.assertEqual(settlement.id, existing.id)

    def test_office_staff_list_is_forced_to_their_own_office(self):
        trip_a = self._make_trip()
        settlement_a, _ = initiate_settlement_for_trip(trip_a)

        trip_b = self._make_trip(
            destination_office=self.other_destination,
            bus=self.other_bus,
            departure_datetime=timezone.now() - timedelta(days=1, hours=3),
            arrival_datetime=timezone.now() - timedelta(days=1, hours=1),
        )
        settlement_b, _ = initiate_settlement_for_trip(trip_b)

        self.client.force_authenticate(self.destination_staff)
        response = self.client.get(f'/api/settlements/?office_id={self.other_destination.id}')

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        returned_ids = {row['id'] for row in response.data['results']}
        self.assertIn(settlement_a.id, returned_ids)
        self.assertNotIn(settlement_b.id, returned_ids)

    def test_refused_cargo_is_excluded_from_cash_and_outstanding(self):
        trip = self._make_trip()
        CargoTicket.objects.create(
            trip=trip,
            ticket_number='CG-001',
            sender_name='Sender',
            receiver_name='Receiver',
            cargo_tier='small',
            price=700,
            currency='DZD',
            payment_source='prepaid',
            status='created',
            created_by=self.conductor,
            synced_at=timezone.now(),
        )
        CargoTicket.objects.create(
            trip=trip,
            ticket_number='CG-002',
            sender_name='Sender',
            receiver_name='Receiver',
            cargo_tier='small',
            price=600,
            currency='DZD',
            payment_source='prepaid',
            status='refused',
            created_by=self.conductor,
            synced_at=timezone.now(),
        )
        CargoTicket.objects.create(
            trip=trip,
            ticket_number='CG-003',
            sender_name='Sender',
            receiver_name='Receiver',
            cargo_tier='medium',
            price=800,
            currency='DZD',
            payment_source='pay_on_delivery',
            status='created',
            created_by=self.conductor,
            synced_at=timezone.now(),
        )
        CargoTicket.objects.create(
            trip=trip,
            ticket_number='CG-004',
            sender_name='Sender',
            receiver_name='Receiver',
            cargo_tier='medium',
            price=900,
            currency='DZD',
            payment_source='pay_on_delivery',
            status='refused',
            created_by=self.conductor,
            synced_at=timezone.now(),
        )

        computation = compute_settlement(trip)

        self.assertEqual(computation.expected_cargo_cash, 700)
        self.assertEqual(computation.outstanding_cargo_delivery, 800)

    def test_record_endpoint_overwrites_partial_and_rejects_terminal_states(self):
        trip = self._make_trip()
        PassengerTicket.objects.create(
            trip=trip,
            ticket_number='PT-002',
            passenger_name='Passenger',
            price=1000,
            currency='DZD',
            payment_source='cash',
            created_by=self.conductor,
            synced_at=timezone.now(),
        )
        settlement, _ = initiate_settlement_for_trip(trip)
        self.client.force_authenticate(self.destination_staff)

        partial_response = self.client.patch(
            f'/api/settlements/{trip.id}/record/',
            {'actual_cash_received': 700, 'actual_expenses_reimbursed': 0, 'notes': 'First attempt'},
            format='json',
        )
        self.assertEqual(partial_response.status_code, status.HTTP_200_OK)
        settlement.refresh_from_db()
        self.assertEqual(settlement.status, Settlement.STATUS_PARTIAL)
        self.assertEqual(settlement.actual_cash_received, 700)

        settled_response = self.client.patch(
            f'/api/settlements/{trip.id}/record/',
            {'actual_cash_received': 1000, 'actual_expenses_reimbursed': 0, 'notes': ''},
            format='json',
        )
        self.assertEqual(settled_response.status_code, status.HTTP_200_OK)
        settlement.refresh_from_db()
        self.assertEqual(settlement.status, Settlement.STATUS_SETTLED)
        self.assertEqual(settlement.actual_cash_received, 1000)
        self.assertEqual(settlement.notes, '')

        rejected_response = self.client.patch(
            f'/api/settlements/{trip.id}/record/',
            {'actual_cash_received': 1000, 'actual_expenses_reimbursed': 0},
            format='json',
        )
        self.assertEqual(rejected_response.status_code, status.HTTP_400_BAD_REQUEST)

        settlement.status = Settlement.STATUS_DISPUTED
        settlement.save(update_fields=['status'])
        disputed_rejected = self.client.patch(
            f'/api/settlements/{trip.id}/record/',
            {'actual_cash_received': 1000, 'actual_expenses_reimbursed': 0},
            format='json',
        )
        self.assertEqual(disputed_rejected.status_code, status.HTTP_400_BAD_REQUEST)

    def test_settlement_audit_entries_are_explicit_and_not_duplicated(self):
        trip = self._make_trip()
        PassengerTicket.objects.create(
            trip=trip,
            ticket_number='PT-003',
            passenger_name='Passenger',
            price=1100,
            currency='DZD',
            payment_source='cash',
            created_by=self.conductor,
            synced_at=timezone.now(),
        )
        self.client.force_authenticate(self.destination_staff)

        initiate_response = self.client.post(f'/api/settlements/initiate/{trip.id}/')
        self.assertEqual(initiate_response.status_code, status.HTTP_201_CREATED)
        record_response = self.client.patch(
            f'/api/settlements/{trip.id}/record/',
            {'actual_cash_received': 1100, 'actual_expenses_reimbursed': 0},
            format='json',
        )
        self.assertEqual(record_response.status_code, status.HTTP_200_OK)

        logs = AuditLog.objects.filter(table_name='settlements').order_by('created_at')
        self.assertEqual(logs.count(), 2)
        self.assertEqual(list(logs.values_list('action', flat=True)), ['create', 'update'])

    @patch(
        'api.views.settlement_views.get_trip_mirror_completion',
        return_value=(True, timezone.now() - timedelta(minutes=5)),
    )
    def test_initiate_reconciles_backend_trip_when_mirror_is_completed(self, mocked_mirror_completion):
        trip = self._make_trip(
            status='in_progress',
            departure_datetime=timezone.now() - timedelta(hours=2),
            arrival_datetime=None,
        )
        self.client.force_authenticate(self.destination_staff)

        response = self.client.post(f'/api/settlements/initiate/{trip.id}/')

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        trip.refresh_from_db()
        self.assertEqual(trip.status, 'completed')
        self.assertIsNotNone(trip.arrival_datetime)
        self.assertTrue(Settlement.objects.filter(trip=trip).exists())
        self.assertEqual(mocked_mirror_completion.call_count, 1)

    def test_compute_settlement_prefers_mirror_computation_when_available(self):
        trip = self._make_trip()
        PassengerTicket.objects.create(
            trip=trip,
            ticket_number='PT-LOCAL-001',
            passenger_name='Backend Rider',
            price=1000,
            currency='DZD',
            payment_source='cash',
            created_by=self.conductor,
            synced_at=timezone.now(),
        )

        mirror_computation = SettlementComputation(
            expected_passenger_cash=7000,
            expected_cargo_cash=2000,
            expected_total_cash=9000,
            agency_presale_total=500,
            outstanding_cargo_delivery=1200,
            expenses_to_reimburse=300,
            net_cash_expected=8700,
            active_passenger_cash_count=7,
            active_passenger_presale_count=1,
            prepaid_cargo_count=2,
            pod_cargo_count=1,
            expense_count=1,
        )

        with patch('api.services.settlements._compute_settlement_from_mirror', return_value=mirror_computation):
            computed = compute_settlement(trip)

        self.assertEqual(computed.expected_total_cash, 9000)
        self.assertEqual(computed.net_cash_expected, 8700)

    def test_closed_trip_sync_is_quarantined_without_mutating_settlement_inputs(self):
        trip = self._make_trip()
        settlement, _ = initiate_settlement_for_trip(trip)
        self.client.force_authenticate(self.conductor)

        response = self.client.post(
            '/api/sync/batch/',
            {
                'trip_id': trip.id,
                'items': [
                    {
                        'type': 'passenger_ticket',
                        'idempotency_key': 'late-ticket',
                        'payload': {'passenger_name': 'Late Rider', 'payment_source': 'cash'},
                    },
                    {
                        'type': 'expense',
                        'idempotency_key': 'late-expense',
                        'payload': {'description': 'Late fuel', 'amount': 300},
                    },
                ],
            },
            format='json',
        )

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['quarantined'], 2)
        self.assertEqual(PassengerTicket.objects.filter(trip=trip).count(), 0)
        self.assertEqual(TripExpense.objects.filter(trip=trip).count(), 0)
        self.assertEqual(QuarantinedSync.objects.filter(trip=trip).count(), 2)
        settlement.refresh_from_db()
        self.assertEqual(settlement.expected_total_cash, 0)
