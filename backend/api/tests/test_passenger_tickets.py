from datetime import date, timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import Bus, Office, PassengerTicket, PricingConfig, Trip, User


class PassengerTicketTests(TestCase):
    """Tests for ticket creation, capacity, price snapshot, and cancel."""

    def setUp(self):
        self.office = Office.objects.create(name='Office A', city='Algiers')
        self.office_b = Office.objects.create(name='Office B', city='Oran')

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

        self.bus = Bus.objects.create(
            plate_number='00001-116-16', capacity=2, office=self.office,
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
            bus=self.bus,
            departure_datetime=timezone.now() + timedelta(hours=1),
            status='scheduled',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )

        self.client = APIClient()

    def test_create_ticket_success(self):
        self.client.force_authenticate(self.conductor)
        resp = self.client.post('/api/tickets/', {
            'trip': self.trip.id,
            'passenger_name': 'John Doe',
        })
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED)
        self.assertEqual(PassengerTicket.objects.count(), 1)
        ticket = PassengerTicket.objects.first()
        self.assertEqual(ticket.price, 1000)

    def test_create_ticket_bus_full(self):
        """Bus capacity=2, creating 3rd ticket fails."""
        for i in range(2):
            PassengerTicket.objects.create(
                trip=self.trip, ticket_number=f'T{i}', price=1000,
                passenger_name=f'P{i}', created_by=self.conductor,
            )
        self.client.force_authenticate(self.conductor)
        resp = self.client.post('/api/tickets/', {
            'trip': self.trip.id,
            'passenger_name': 'Overflow',
        })
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn('capacity', str(resp.data).lower())

    def test_price_snapshot_frozen(self):
        """Ticket price stays frozen even if trip price changes."""
        ticket = PassengerTicket.objects.create(
            trip=self.trip, ticket_number='T1', price=1000,
            passenger_name='John', created_by=self.conductor,
        )
        self.trip.passenger_base_price = 1500
        self.trip.save(skip_validation=True)
        ticket.refresh_from_db()
        self.assertEqual(ticket.price, 1000)

    def test_create_ticket_completed_trip_fails(self):
        self.trip.status = 'completed'
        self.trip.save(skip_validation=True)
        self.client.force_authenticate(self.conductor)
        resp = self.client.post('/api/tickets/', {
            'trip': self.trip.id,
            'passenger_name': 'Late',
        })
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)

    def test_cancel_ticket(self):
        ticket = PassengerTicket.objects.create(
            trip=self.trip, ticket_number='T1', price=1000,
            passenger_name='John', created_by=self.conductor,
        )
        self.client.force_authenticate(self.conductor)
        resp = self.client.post(f'/api/tickets/{ticket.id}/cancel/')
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        ticket.refresh_from_db()
        self.assertEqual(ticket.status, 'cancelled')

    def test_cancelled_ticket_frees_capacity(self):
        """After cancelling, a new ticket can be created."""
        for i in range(2):
            PassengerTicket.objects.create(
                trip=self.trip, ticket_number=f'T{i}', price=1000,
                passenger_name=f'P{i}', created_by=self.conductor,
            )
        t = PassengerTicket.objects.first()
        t.status = 'cancelled'
        t.save()

        self.client.force_authenticate(self.conductor)
        resp = self.client.post('/api/tickets/', {
            'trip': self.trip.id,
            'passenger_name': 'New',
        })
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED)

    def test_wrong_conductor_cannot_sell(self):
        other = User.objects.create_user(
            phone='0550000099', password='pass',
            first_name='Other', last_name='Cond',
            role='conductor', office=self.office,
        )
        self.client.force_authenticate(other)
        resp = self.client.post('/api/tickets/', {
            'trip': self.trip.id,
            'passenger_name': 'X',
        })
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)

    def test_staff_can_sell_tickets(self):
        self.client.force_authenticate(self.staff)
        resp = self.client.post('/api/tickets/', {
            'trip': self.trip.id,
            'passenger_name': 'VIP',
        })
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED)
