from datetime import timedelta
from io import StringIO

from django.core.management import call_command
from django.test import TestCase
from django.utils import timezone

from api.models import Bus, Office, PassengerTicket, Trip, TripExpense, User
from api.services import initiate_settlement_for_trip


class RepairMoneyScaleCommandTests(TestCase):
    def setUp(self):
        self.origin = Office.objects.create(name='Repair Origin', city='Algiers')
        self.destination = Office.objects.create(name='Repair Destination', city='Oran')
        self.conductor = User.objects.create_user(
            phone='0551999999',
            password='pass',
            first_name='Repair',
            last_name='Conductor',
            role='conductor',
            office=self.origin,
        )
        self.bus = Bus.objects.create(
            plate_number='REPAIR-001',
            capacity=49,
            office=self.origin,
        )
        self.trip = Trip.objects.create(
            origin_office=self.origin,
            destination_office=self.destination,
            conductor=self.conductor,
            bus=self.bus,
            departure_datetime=timezone.now(),
            arrival_datetime=timezone.now() + timedelta(hours=4),
            status='completed',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )

    def test_apply_repairs_updates_rows_and_refreshes_settlement(self):
        ticket = PassengerTicket.objects.create(
            trip=self.trip,
            ticket_number='PT-REPAIR-001',
            passenger_name='Legacy Price',
            price=100000,
            currency='DZD',
            payment_source='cash',
            created_by=self.conductor,
        )
        expense = TripExpense.objects.create(
            trip=self.trip,
            description='',
            amount=380500,
            currency='DZD',
            synced_at=timezone.now(),
            created_by=self.conductor,
        )
        settlement, _ = initiate_settlement_for_trip(self.trip)
        self.assertEqual(settlement.expected_total_cash, 100000)
        self.assertEqual(settlement.expenses_to_reimburse, 380500)

        stdout = StringIO()
        call_command('repair_money_scale', '--apply', '--trip-id', str(self.trip.id), stdout=stdout)

        ticket.refresh_from_db()
        expense.refresh_from_db()
        settlement.refresh_from_db()

        self.assertEqual(ticket.price, 1000)
        self.assertEqual(expense.amount, 3805)
        self.assertEqual(settlement.expected_total_cash, 1000)
        self.assertEqual(settlement.expenses_to_reimburse, 3805)
        self.assertEqual(settlement.net_cash_expected, -2805)
