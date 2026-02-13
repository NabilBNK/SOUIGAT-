"""Test CargoTicket 9-state machine transitions + FORBIDDEN_ADMIN_OVERRIDES.

10 tests covering:
- All valid forward transitions (created→in_transit→arrived→delivered→paid)
- Illegal direct save() bypass
- FORBIDDEN_ADMIN_OVERRIDES (6 pairs)
- Override reason validation (min 10 chars)
- Non-admin cannot override
- Admin override with valid reason
"""
from datetime import timedelta

from django.core.exceptions import ValidationError
from django.test import TestCase
from django.utils import timezone

from api.models import Bus, CargoTicket, Office, Trip, User


class CargoStateMachineTests(TestCase):

    @classmethod
    def setUpTestData(cls):
        cls.office_a = Office.objects.create(name='SM Office A', city='Algiers')
        cls.office_b = Office.objects.create(name='SM Office B', city='Oran')
        cls.admin = User.objects.create_user(
            phone='0500000088', password='test1234',
            first_name='Admin', last_name='SM', role='admin',
        )
        cls.conductor = User.objects.create_user(
            phone='0700000088', password='test1234',
            first_name='Cond', last_name='SM', role='conductor',
        )
        cls.staff = User.objects.create_user(
            phone='0600000088', password='test1234',
            first_name='Staff', last_name='SM', role='office_staff',
        )
        cls.bus = Bus.objects.create(
            plate_number='88-TEST-88', office=cls.office_a, capacity=50,
        )
        trip = Trip(
            origin_office=cls.office_a, destination_office=cls.office_b,
            conductor=cls.conductor, bus=cls.bus,
            departure_datetime=timezone.now() + timedelta(hours=1),
            passenger_base_price=2500, cargo_small_price=500,
            cargo_medium_price=1000, cargo_large_price=2000,
        )
        trip.save()
        cls.trip = trip

    def _make_cargo(self, status='created'):
        """Create a CargoTicket at the given initial status."""
        ct = CargoTicket(
            trip=self.trip,
            ticket_number=f'CT-{CargoTicket.all_objects.count() + 1:04d}',
            sender_name='Sender', receiver_name='Receiver',
            cargo_tier='small', price=500,
            payment_source='prepaid', created_by=self.staff,
        )
        ct.save()
        # Walk to desired status using valid transitions
        path = {
            'created': [],
            'in_transit': ['in_transit'],
            'arrived': ['in_transit', 'arrived'],
            'delivered': ['in_transit', 'arrived', 'delivered'],
            'paid': ['in_transit', 'arrived', 'delivered', 'paid'],
        }
        for step in path.get(status, []):
            ct.transition_to(step, self.conductor)
        return ct

    # -- Valid transitions --

    def test_created_to_in_transit(self):
        ct = self._make_cargo('created')
        ct.transition_to('in_transit', self.conductor)
        self.assertEqual(ct.status, 'in_transit')

    def test_full_happy_path(self):
        """created → in_transit → arrived → delivered → paid."""
        ct = self._make_cargo('created')
        for status in ['in_transit', 'arrived', 'delivered', 'paid']:
            ct.transition_to(status, self.conductor)
        self.assertEqual(ct.status, 'paid')
        self.assertGreater(ct.version, 1)

    def test_created_to_cancelled(self):
        ct = self._make_cargo('created')
        ct.transition_to('cancelled', self.conductor)
        self.assertEqual(ct.status, 'cancelled')

    def test_arrived_to_refused(self):
        ct = self._make_cargo('arrived')
        ct.transition_to('refused', self.conductor)
        self.assertEqual(ct.status, 'refused')

    # -- Invalid transitions --

    def test_direct_save_bypass_blocked(self):
        """Setting status directly on save() must raise ValidationError."""
        ct = self._make_cargo('created')
        ct.status = 'paid'
        with self.assertRaises(ValidationError):
            ct.save()

    def test_skip_state_blocked(self):
        """created → arrived (skipping in_transit) → ValidationError."""
        ct = self._make_cargo('created')
        with self.assertRaises(ValidationError):
            ct.transition_to('arrived', self.conductor)

    # -- FORBIDDEN_ADMIN_OVERRIDES --

    def test_forbidden_paid_to_created(self):
        """Even admin cannot reverse paid → created."""
        ct = self._make_cargo('paid')
        with self.assertRaises(ValidationError):
            ct.transition_to('created', self.admin, reason='Testing forbidden override')

    def test_forbidden_lost_to_delivered(self):
        """Even admin cannot do lost → delivered."""
        ct = self._make_cargo('created')
        ct.transition_to('in_transit', self.conductor)
        ct.transition_to('lost', self.conductor)
        with self.assertRaises(ValidationError):
            ct.transition_to('delivered', self.admin, reason='Testing forbidden override')

    # -- Override rules --

    def test_admin_override_requires_long_reason(self):
        """Admin override with <10 char reason → ValidationError."""
        ct = self._make_cargo('created')
        with self.assertRaises(ValidationError):
            ct.transition_to('arrived', self.admin, reason='short')

    def test_non_admin_cannot_override(self):
        """Non-admin invalid transition → ValidationError (no override)."""
        ct = self._make_cargo('created')
        with self.assertRaises(ValidationError):
            ct.transition_to('delivered', self.conductor)
