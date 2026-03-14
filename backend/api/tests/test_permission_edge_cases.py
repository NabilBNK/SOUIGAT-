from datetime import timedelta
from django.core.cache import cache
from django.test import TestCase, override_settings
from django.utils import timezone
from django.contrib.auth.models import AnonymousUser
from rest_framework.test import APIClient
from rest_framework.exceptions import AuthenticationFailed

from api.models import User, Office, Trip, Bus
from api.permissions import DeviceBoundPermission, OfficeScopePermission


class FakeRequest:
    def __init__(self, user, auth=None):
        self.user = user
        self.auth = auth
        self.method = 'GET'


class PermissionEdgeCaseTests(TestCase):
    def setUp(self):
        self.office_a = Office.objects.create(name='Office A', city='City A')
        self.office_b = Office.objects.create(name='Office B', city='City B')
        self.office_c = Office.objects.create(name='Office C', city='City C')

        self.user_a = User.objects.create_user(
            phone='0550000001', password='p', first_name='A', last_name='A', role='office_staff', office=self.office_a
        )
        self.user_b = User.objects.create_user(
            phone='0550000002', password='p', first_name='B', last_name='B', role='office_staff', office=self.office_b
        )
        self.user_c = User.objects.create_user(
            phone='0550000003', password='p', first_name='C', last_name='C', role='office_staff', office=self.office_c
        )
        self.admin = User.objects.create_user(
            phone='0550000000', password='p', first_name='Admin', last_name='User', role='admin'
        )

        self.conductor = User.objects.create_user(
            phone='0550000004', password='p', first_name='Cond', last_name='U', role='conductor', office=self.office_a
        )
        self.bus = Bus.objects.create(plate_number='00001-116-16', capacity=50, office=self.office_a)


    def test_device_bound_permission_anonymous_user(self):
        """DeviceBoundPermission should allow anonymous users (delegating to IsAuthenticated)."""
        request = FakeRequest(AnonymousUser(), auth=None)
        perm = DeviceBoundPermission()
        # Should return True, NOT crash
        self.assertTrue(perm.has_permission(request, None))

    def test_office_scope_trip_access(self):
        """OfficeScopePermission should allow access if user is in origin OR destination office."""
        trip = Trip.objects.create(
            origin_office=self.office_a,
            destination_office=self.office_b,
            bus=self.bus,
            conductor=self.conductor,
            departure_datetime=timezone.now() + timedelta(hours=1),
            passenger_base_price=100,
            cargo_small_price=10,
            cargo_medium_price=20,
            cargo_large_price=30,
        )

        perm = OfficeScopePermission()

        # Origin office user -> True
        self.assertTrue(perm.has_object_permission(FakeRequest(self.user_a), None, trip))

        # Destination office user -> True
        self.assertTrue(perm.has_object_permission(FakeRequest(self.user_b), None, trip))

        # Unrelated office user -> False
        self.assertFalse(perm.has_object_permission(FakeRequest(self.user_c), None, trip))

        # Admin -> True
        self.assertTrue(perm.has_object_permission(FakeRequest(self.admin), None, trip))

    def test_office_scope_conductor_scoped_by_assignment_not_office(self):
        """Conductor access must be based on assigned trip, even if office differs."""
        trip = Trip.objects.create(
            origin_office=self.office_b,
            destination_office=self.office_c,
            bus=self.bus,
            conductor=self.conductor,
            departure_datetime=timezone.now() + timedelta(hours=3),
            passenger_base_price=100,
            cargo_small_price=10,
            cargo_medium_price=20,
            cargo_large_price=30,
        )

        other_conductor = User.objects.create_user(
            phone='0550000015', password='p', first_name='Other', last_name='Cond', role='conductor', office=self.office_b
        )

        perm = OfficeScopePermission()

        self.assertTrue(perm.has_object_permission(FakeRequest(self.conductor), None, trip))
        self.assertFalse(perm.has_object_permission(FakeRequest(other_conductor), None, trip))

    def test_conductor_can_retrieve_assigned_trip_with_cross_office_route(self):
        """API retrieve must allow assigned conductor even if offices don't match conductor.office."""
        trip = Trip.objects.create(
            origin_office=self.office_b,
            destination_office=self.office_c,
            bus=self.bus,
            conductor=self.conductor,
            departure_datetime=timezone.now() + timedelta(hours=4),
            passenger_base_price=100,
            cargo_small_price=10,
            cargo_medium_price=20,
            cargo_large_price=30,
        )

        other_conductor = User.objects.create_user(
            phone='0550000016', password='p', first_name='Else', last_name='Cond', role='conductor', office=self.office_c
        )

        client = APIClient()
        client.force_authenticate(user=self.conductor)
        allowed = client.get(f'/api/trips/{trip.id}/')
        self.assertEqual(allowed.status_code, 200)

        client.force_authenticate(user=other_conductor)
        denied = client.get(f'/api/trips/{trip.id}/')
        self.assertEqual(denied.status_code, 404)

    def test_grace_period_cache_none(self):
        """If cache returns None for grace user ID, it should not match unrelated user."""
        # Setup: User A has device-1. JWT has device-2.
        # We manually call logic or simulate it.
        # Hard to simulate DeviceBoundPermission internals with FakeRequest exactly as cache dependency is tricky.
        # But we can verify the 'check' logic by constructing a request with matching payload.
        
        # Helper to simulate auth token payload
        class FakeToken:
            payload = {'device_id': 'device-2'}

        request = FakeRequest(self.user_a, auth=FakeToken())
        self.user_a.device_id = 'device-1' # Mismatch

        # Ensure cache for 'grace_device:device-2' is empty (None)
        cache.delete('grace_device:device-2')

        perm = DeviceBoundPermission()
        
        # Should raise AuthenticationFailed
        with self.assertRaises(AuthenticationFailed) as cm:
            perm.has_permission(request, None)
        
        self.assertEqual(str(cm.exception.detail), 'Device not bound to this account.')

    def test_trip_reference_data_office_staff_allowed(self):
        """Office staff can fetch trip creation reference data from non-admin endpoint."""
        client = APIClient()
        client.force_authenticate(user=self.user_a)
        resp = client.get('/api/trips/reference-data/')
        self.assertEqual(resp.status_code, 200)
        self.assertIn('offices', resp.data)
        self.assertIn('buses', resp.data)
        self.assertIn('conductors', resp.data)

    def test_trip_reference_data_conductor_forbidden(self):
        """Conductor cannot fetch trip creation reference data (no create_trip permission)."""
        client = APIClient()
        client.force_authenticate(user=self.conductor)
        resp = client.get('/api/trips/reference-data/')
        self.assertEqual(resp.status_code, 403)
