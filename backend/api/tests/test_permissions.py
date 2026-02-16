from django.test import TestCase

from api.models import User
from api.models.office import Office
from api.permissions import (
    DepartmentPermission,
    OfficeScopePermission,
    RBACPermission,
    TripStatusPermission,
    get_user_permissions,
)


class FakeRequest:
    def __init__(self, user):
        self.user = user
        self.method = 'GET'
        self.auth = None


class FakeView:
    required_roles = None
    required_department = None


class PermissionMatrixTests(TestCase):
    """Tests for get_user_permissions and PERMISSION_MATRIX."""

    def setUp(self):
        self.office = Office.objects.create(name='Oran', city='Oran')

    def test_admin_gets_all_admin_permissions(self):
        admin = User.objects.create_user(
            phone='0550000001', password='p', first_name='A', last_name='A', role='admin',
        )
        perms = get_user_permissions(admin)
        self.assertIn('manage_users', perms)
        self.assertIn('revoke_devices', perms)

    def test_office_staff_cargo_dept(self):
        user = User.objects.create_user(
            phone='0550000002', password='p', first_name='B', last_name='B',
            role='office_staff', department='cargo', office=self.office,
        )
        perms = get_user_permissions(user)
        self.assertIn('create_cargo_ticket', perms)
        self.assertNotIn('create_passenger_ticket', perms)

    def test_conductor_permissions(self):
        user = User.objects.create_user(
            phone='0550000003', password='p', first_name='C', last_name='C',
            role='conductor', office=self.office,
        )
        perms = get_user_permissions(user)
        self.assertIn('sync_batch', perms)
        self.assertNotIn('manage_users', perms)


class RBACPermissionTests(TestCase):

    def setUp(self):
        self.office = Office.objects.create(name='Test', city='Test')
        self.perm = RBACPermission()

    def test_admin_passes_admin_required(self):
        admin = User.objects.create_user(
            phone='0550000010', password='p', first_name='A', last_name='A', role='admin',
        )
        view = FakeView()
        view.required_roles = ['admin']
        self.assertTrue(self.perm.has_permission(FakeRequest(admin), view))

    def test_office_staff_blocked_from_admin(self):
        staff = User.objects.create_user(
            phone='0550000011', password='p', first_name='B', last_name='B',
            role='office_staff', department='all', office=self.office,
        )
        view = FakeView()
        view.required_roles = ['admin']
        self.assertFalse(self.perm.has_permission(FakeRequest(staff), view))


class DepartmentPermissionTests(TestCase):

    def setUp(self):
        self.office = Office.objects.create(name='Test', city='Test')
        self.perm = DepartmentPermission()

    def test_cargo_blocked_from_passenger(self):
        user = User.objects.create_user(
            phone='0550000020', password='p', first_name='C', last_name='C',
            role='office_staff', department='cargo', office=self.office,
        )
        view = FakeView()
        view.required_department = 'passenger'
        self.assertFalse(self.perm.has_permission(FakeRequest(user), view))

    def test_all_dept_passes_any(self):
        user = User.objects.create_user(
            phone='0550000021', password='p', first_name='D', last_name='D',
            role='office_staff', department='all', office=self.office,
        )
        view = FakeView()
        view.required_department = 'cargo'
        self.assertTrue(self.perm.has_permission(FakeRequest(user), view))


class TripStatusPermissionTests(TestCase):

    def setUp(self):
        self.perm = TripStatusPermission()

    def test_write_blocked_on_completed_trip(self):
        trip = type('Trip', (), {'status': 'completed'})()
        req = FakeRequest(None)
        req.method = 'PATCH'
        self.assertFalse(self.perm.has_object_permission(req, FakeView(), trip))

    def test_read_allowed_on_completed_trip(self):
        trip = type('Trip', (), {'status': 'completed'})()
        req = FakeRequest(None)
        req.method = 'GET'
        self.assertTrue(self.perm.has_object_permission(req, FakeView(), trip))
