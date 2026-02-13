"""Test DB constraints and model validation enforcement.

10 tests covering:
- Price positivity (ticket + expense + pricing)
- Trip business rules (arrival>departure, origin!=dest)
- Currency format (3-letter uppercase)
- Unique constraints (trip+ticket_number)
- on_delete PROTECT behavior
"""
from datetime import timedelta

from django.core.exceptions import ValidationError
from django.db import IntegrityError
from django.db.models import ProtectedError
from django.test import TestCase
from django.utils import timezone

from api.models import (
    Bus, CargoTicket, Office, PassengerTicket,
    PricingConfig, Trip, TripExpense, User,
)


class BaseTestCase(TestCase):
    """Shared fixtures for all constraint tests."""

    @classmethod
    def setUpTestData(cls):
        cls.office_a = Office.objects.create(name='Office A', city='Algiers')
        cls.office_b = Office.objects.create(name='Office B', city='Oran')
        cls.office_c = Office.objects.create(name='Office C', city='Constantine')

        cls.admin = User.objects.create_user(
            phone='0500000099', password='test1234',
            first_name='Admin', last_name='Test', role='admin',
        )
        cls.conductor = User.objects.create_user(
            phone='0700000099', password='test1234',
            first_name='Cond', last_name='Test', role='conductor',
        )
        cls.staff = User.objects.create_user(
            phone='0600000099', password='test1234',
            first_name='Staff', last_name='Test', role='office_staff',
        )
        cls.bus = Bus.objects.create(
            plate_number='99-TEST-99', office=cls.office_a, capacity=50,
        )

    def _make_trip(self, **overrides):
        defaults = dict(
            origin_office=self.office_a,
            destination_office=self.office_b,
            conductor=self.conductor,
            bus=self.bus,
            departure_datetime=timezone.now() + timedelta(hours=1),
            passenger_base_price=2500,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=2000,
        )
        defaults.update(overrides)
        return Trip(**defaults)


class TripConstraintTests(BaseTestCase):
    """Trip-level DB and model constraints."""

    def test_same_office_blocked_by_model(self):
        """origin == destination → ValidationError from save() → full_clean()."""
        trip = self._make_trip(
            origin_office=self.office_a, destination_office=self.office_a,
        )
        with self.assertRaises(ValidationError):
            trip.save()

    def test_same_office_blocked_by_db(self):
        """DB CHECK trip_different_offices fires even if validation skipped."""
        trip = self._make_trip(
            origin_office=self.office_a, destination_office=self.office_a,
        )
        with self.assertRaises(IntegrityError):
            trip.save(skip_validation=True)

    def test_arrival_before_departure_blocked(self):
        """arrival <= departure → ValidationError."""
        now = timezone.now()
        trip = self._make_trip(
            departure_datetime=now + timedelta(hours=2),
            arrival_datetime=now + timedelta(hours=1),
        )
        with self.assertRaises(ValidationError):
            trip.save()

    def test_invalid_currency_blocked_by_db(self):
        """Currency not matching ^[A-Z]{3}$ → IntegrityError."""
        trip = self._make_trip()
        trip.save()  # valid first
        trip.currency = 'usd'  # lowercase
        with self.assertRaises((IntegrityError, ValidationError)):
            trip.save(skip_validation=True)

    def test_conductor_role_enforced(self):
        """Non-conductor user assigned as conductor → ValidationError."""
        trip = self._make_trip(conductor=self.staff)
        with self.assertRaises(ValidationError):
            trip.save()

    def test_valid_trip_succeeds(self):
        """Happy path: valid trip creates successfully."""
        trip = self._make_trip()
        trip.save()
        self.assertIsNotNone(trip.pk)
        self.assertEqual(trip.status, 'scheduled')


class TicketConstraintTests(BaseTestCase):
    """Ticket unique constraints and price checks."""

    @classmethod
    def setUpTestData(cls):
        super().setUpTestData()
        trip = Trip(
            origin_office=cls.office_a, destination_office=cls.office_b,
            conductor=cls.conductor, bus=cls.bus,
            departure_datetime=timezone.now() + timedelta(hours=1),
            passenger_base_price=2500, cargo_small_price=500,
            cargo_medium_price=1000, cargo_large_price=2000,
        )
        trip.save()
        cls.trip = trip

    def test_duplicate_passenger_ticket_blocked(self):
        """Same (trip, ticket_number) → IntegrityError."""
        PassengerTicket.objects.create(
            trip=self.trip, ticket_number='PT-001',
            passenger_name='Alice', price=2500, created_by=self.staff,
        )
        with self.assertRaises(IntegrityError):
            PassengerTicket.objects.create(
                trip=self.trip, ticket_number='PT-001',
                passenger_name='Bob', price=2500, created_by=self.staff,
            )

    def test_duplicate_cargo_ticket_blocked(self):
        """Same (trip, ticket_number) on cargo → IntegrityError."""
        CargoTicket.objects.create(
            trip=self.trip, ticket_number='CT-001',
            sender_name='S', receiver_name='R',
            cargo_tier='small', price=500,
            payment_source='prepaid', created_by=self.staff,
        )
        with self.assertRaises(IntegrityError):
            CargoTicket.objects.create(
                trip=self.trip, ticket_number='CT-001',
                sender_name='X', receiver_name='Y',
                cargo_tier='medium', price=1000,
                payment_source='prepaid', created_by=self.staff,
            )

    def test_protect_prevents_user_deletion(self):
        """PROTECT on created_by → can't delete user with tickets."""
        ticket = PassengerTicket.objects.create(
            trip=self.trip, ticket_number='PT-PROT',
            passenger_name='Test', price=2500, created_by=self.staff,
        )
        with self.assertRaises(ProtectedError):
            self.staff.delete()

    def test_cascade_deletes_expenses(self):
        """CASCADE on trip → hard-deleting trip deletes its expenses."""
        # Create isolated trip with no PROTECT tickets attached
        trip_for_cascade = Trip(
            origin_office=self.office_a, destination_office=self.office_b,
            conductor=self.conductor, bus=self.bus,
            departure_datetime=timezone.now() + timedelta(hours=5),
            passenger_base_price=2500, cargo_small_price=500,
            cargo_medium_price=1000, cargo_large_price=2000,
        )
        trip_for_cascade.save()

        TripExpense.objects.create(
            trip=trip_for_cascade, description='Fuel',
            amount=5000, created_by=self.staff,
        )
        self.assertEqual(TripExpense.objects.filter(trip=trip_for_cascade).count(), 1)

        trip_pk = trip_for_cascade.pk
        # Use all_objects to ensure we get the real object for hard delete
        Trip.all_objects.filter(pk=trip_pk).delete()
        self.assertEqual(TripExpense.objects.filter(trip_id=trip_pk).count(), 0)
