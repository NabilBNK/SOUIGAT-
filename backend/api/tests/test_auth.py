from django.core.cache import cache
from django.test import TestCase, override_settings
from rest_framework.test import APIClient

from api.models import User
from api.models.office import Office


@override_settings(
    CACHES={'default': {'BACKEND': 'django.core.cache.backends.locmem.LocMemCache'}},
)
class JWTAuthTests(TestCase):

    def setUp(self):
        self.client = APIClient()
        self.office = Office.objects.create(name='Alger Centre', city='Algiers')

        self.web_user = User.objects.create_user(
            phone='0661111111',
            password='testpass123',
            first_name='Web',
            last_name='User',
            role='office_staff',
            department='all',
            office=self.office,
        )
        self.conductor = User.objects.create_user(
            phone='0662222222',
            password='testpass123',
            first_name='Conductor',
            last_name='User',
            role='conductor',
            office=self.office,
        )

    def tearDown(self):
        cache.clear()

    # --- Login ---

    def test_web_login_success(self):
        resp = self.client.post('/api/auth/login/', {
            'phone': '0661111111',
            'password': 'testpass123',
            'platform': 'web',
        })
        self.assertEqual(resp.status_code, 200)
        self.assertIn('access', resp.data)
        self.assertIn('refresh', resp.data)
        self.assertEqual(resp.data['token_strategy'], 'rotating')
        self.assertFalse(resp.data['device_bound'])

    def test_mobile_login_success_binds_device(self):
        resp = self.client.post('/api/auth/login/', {
            'phone': '0662222222',
            'password': 'testpass123',
            'device_id': 'android-device-abc123',
            'platform': 'mobile',
        })
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.data['token_strategy'], 'persistent')
        self.assertTrue(resp.data['device_bound'])

        self.conductor.refresh_from_db()
        self.assertEqual(self.conductor.device_id, 'android-device-abc123')
        self.assertIsNotNone(self.conductor.device_bound_at)

    def test_mobile_login_without_device_id_fails(self):
        resp = self.client.post('/api/auth/login/', {
            'phone': '0662222222',
            'password': 'testpass123',
            'platform': 'mobile',
        })
        self.assertEqual(resp.status_code, 400)

    def test_conductor_web_login_allowed(self):
        """Conductor can currently login via web platform.
        
        NOTE: If business requires restricting conductors to mobile-only,
        add a role check in LoginSerializer.validate().
        """
        resp = self.client.post('/api/auth/login/', {
            'phone': '0662222222',
            'password': 'testpass123',
            'platform': 'web',
        })
        self.assertEqual(resp.status_code, 200)

    def test_wrong_password_returns_generic_error(self):
        resp = self.client.post('/api/auth/login/', {
            'phone': '0661111111',
            'password': 'wrongpass',
            'platform': 'web',
        })
        self.assertEqual(resp.status_code, 400)
        self.assertNotIn('password', str(resp.data))

    def test_inactive_user_login_fails(self):
        self.web_user.is_active = False
        self.web_user.save(update_fields=['is_active'])

        resp = self.client.post('/api/auth/login/', {
            'phone': '0661111111',
            'password': 'testpass123',
            'platform': 'web',
        })
        self.assertEqual(resp.status_code, 400)

    # --- Token Refresh ---

    def test_web_refresh_rotates_token(self):
        login = self.client.post('/api/auth/login/', {
            'phone': '0661111111',
            'password': 'testpass123',
            'platform': 'web',
        })
        old_refresh = login.data['refresh']

        resp = self.client.post('/api/auth/token/refresh/', {
            'refresh': old_refresh,
        })
        self.assertEqual(resp.status_code, 200)
        self.assertIn('refresh', resp.data)
        self.assertNotEqual(resp.data['refresh'], old_refresh)
        self.assertEqual(resp.data['strategy'], 'rotated')

        # Old token blacklisted
        retry = self.client.post('/api/auth/token/refresh/', {
            'refresh': old_refresh,
        })
        self.assertEqual(retry.status_code, 401)

    def test_mobile_refresh_persistent(self):
        login = self.client.post('/api/auth/login/', {
            'phone': '0662222222',
            'password': 'testpass123',
            'device_id': 'android-12345',
            'platform': 'mobile',
        })
        old_refresh = login.data['refresh']

        resp = self.client.post('/api/auth/token/refresh/', {
            'refresh': old_refresh,
            'device_id': 'android-12345',
        })
        self.assertEqual(resp.status_code, 200)
        self.assertNotIn('refresh', resp.data)
        self.assertIn('access', resp.data)
        self.assertEqual(resp.data['strategy'], 'persistent')

        # Old refresh still valid
        retry = self.client.post('/api/auth/token/refresh/', {
            'refresh': old_refresh,
            'device_id': 'android-12345',
        })
        self.assertEqual(retry.status_code, 200)

    # --- /me ---

    def test_me_returns_profile_with_permissions(self):
        self.client.force_authenticate(user=self.conductor)
        resp = self.client.get('/api/auth/me/')

        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.data['role'], 'conductor')
        self.assertIn('permissions', resp.data)
        self.assertIn('create_passenger_ticket', resp.data['permissions'])

    # --- Logout ---

    def test_logout_blacklists_token(self):
        login = self.client.post('/api/auth/login/', {
            'phone': '0661111111',
            'password': 'testpass123',
            'platform': 'web',
        })

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {login.data['access']}")
        resp = self.client.post('/api/auth/logout/', {
            'refresh': login.data['refresh'],
        })
        self.assertEqual(resp.status_code, 200)

        # Refresh should now fail
        retry = self.client.post('/api/auth/token/refresh/', {
            'refresh': login.data['refresh'],
        })
        self.assertEqual(retry.status_code, 401)

    # --- Rate Limiting ---

    def test_login_rate_limit(self):
        for _ in range(6):
            resp = self.client.post('/api/auth/login/', {
                'phone': '0669999999',
                'password': 'wrong',
                'platform': 'web',
            })
        self.assertEqual(resp.status_code, 429)

    # --- Device Switch ---

    def test_device_switch_creates_grace_period(self):
        # First login
        self.client.post('/api/auth/login/', {
            'phone': '0662222222',
            'password': 'testpass123',
            'device_id': 'phone-A',
            'platform': 'mobile',
        })

        # Second login (different device)
        self.client.post('/api/auth/login/', {
            'phone': '0662222222',
            'password': 'testpass123',
            'device_id': 'phone-B',
            'platform': 'mobile',
        })

        # Grace period set for phone-A
        grace = cache.get('grace_device:phone-A')
        self.assertEqual(grace, self.conductor.id)

        self.conductor.refresh_from_db()
        self.assertEqual(self.conductor.device_id, 'phone-B')

    def test_old_device_passes_within_grace_period(self):
        # Login on phone-A, then switch to phone-B
        login_a = self.client.post('/api/auth/login/', {
            'phone': '0662222222',
            'password': 'testpass123',
            'device_id': 'phone-A',
            'platform': 'mobile',
        })
        self.client.post('/api/auth/login/', {
            'phone': '0662222222',
            'password': 'testpass123',
            'device_id': 'phone-B',
            'platform': 'mobile',
        })

        # Use phone-A's token to access /me (should work within grace)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {login_a.data['access']}")
        resp = self.client.get('/api/auth/me/')
        self.assertEqual(resp.status_code, 200)
