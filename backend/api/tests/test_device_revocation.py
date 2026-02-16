from django.core.cache import cache
from django.test import TestCase, override_settings
from rest_framework.test import APIClient

from api.models import User
from api.models.office import Office


@override_settings(
    CACHES={'default': {'BACKEND': 'django.core.cache.backends.locmem.LocMemCache'}},
)
class DeviceRevocationTests(TestCase):

    def setUp(self):
        self.client = APIClient()
        self.office = Office.objects.create(name='Constantine', city='Constantine')

        self.admin = User.objects.create_user(
            phone='0550000100',
            password='adminpass',
            first_name='Admin',
            last_name='User',
            role='admin',
        )
        self.conductor = User.objects.create_user(
            phone='0550000101',
            password='testpass',
            first_name='Conductor',
            last_name='User',
            role='conductor',
            office=self.office,
        )

    def tearDown(self):
        cache.clear()

    def _login_conductor(self, device_id='android-test'):
        resp = self.client.post('/api/auth/login/', {
            'phone': '0550000101',
            'password': 'testpass',
            'device_id': device_id,
            'platform': 'mobile',
        })
        return resp.data

    def test_revoked_device_returns_401(self):
        tokens = self._login_conductor('device-to-revoke')

        # Admin revokes device
        self.client.force_authenticate(user=self.admin)
        resp = self.client.post(f'/api/admin/users/{self.conductor.pk}/revoke-device/')
        self.assertEqual(resp.status_code, 200)

        # Clear admin auth to simulate conductor request properly
        self.client.force_authenticate(user=None)

        # Ban device in Redis (simulating what the view does, or view does it)
        # The view invalidates for 30 days.
        # But we use LocMemCache in tests. View uses cache.
        # They share cache. So view action sets the key.
        # We don't need to manually set it unless view failed.

        # Conductor tries to access protected endpoint
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {tokens['access']}")
        resp = self.client.get('/api/auth/me/')
        self.assertEqual(resp.status_code, 401)

    def test_non_revoked_device_passes(self):
        tokens = self._login_conductor()

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {tokens['access']}")
        resp = self.client.get('/api/auth/me/')
        self.assertEqual(resp.status_code, 200)

    def test_revoke_endpoint_admin_only(self):
        self._login_conductor()

        # Conductor tries to revoke
        self.client.force_authenticate(user=self.conductor)
        resp = self.client.post(f'/api/admin/users/{self.conductor.pk}/revoke-device/')
        self.assertEqual(resp.status_code, 403)

    def test_re_login_after_revocation(self):
        self._login_conductor('old-device')

        # Admin revokes
        self.client.force_authenticate(user=self.admin)
        self.client.post(f'/api/admin/users/{self.conductor.pk}/revoke-device/')

        # Conductor re-logs with new device
        self.client.logout()
        tokens = self._login_conductor('new-device')
        self.conductor.refresh_from_db()
        self.assertEqual(self.conductor.device_id, 'new-device')

        # New token works
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {tokens['access']}")
        resp = self.client.get('/api/auth/me/')
        self.assertEqual(resp.status_code, 200)

    def test_grace_period_allows_old_device(self):
        tokens_a = self._login_conductor('phone-A')

        # Switch to phone-B
        self._login_conductor('phone-B')

        # phone-A's token should still work (grace period)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {tokens_a['access']}")
        resp = self.client.get('/api/auth/me/')
        self.assertEqual(resp.status_code, 200)

    def test_rescue_endpoint_extends_grace(self):
        self._login_conductor('phone-A')
        self._login_conductor('phone-B')

        # Admin rescues phone-A for 1 hour
        self.client.force_authenticate(user=self.admin)
        resp = self.client.post(
            f'/api/admin/users/{self.conductor.pk}/rescue-device/',
            {'device_id': 'phone-A'},
        )
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.data['grace_timeout'], 3600)
