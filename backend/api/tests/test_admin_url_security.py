"""Test that Django admin is NOT at the default /admin/ URL."""
from django.test import TestCase, RequestFactory


class AdminURLSecurityTests(TestCase):
    """Default /admin/ path should not serve the admin panel."""

    def test_default_admin_path_returns_404(self):
        """GET /admin/ must NOT serve Django admin login page."""
        resp = self.client.get('/admin/')
        self.assertEqual(
            resp.status_code, 404,
            'Django admin is still exposed at the default /admin/ URL. '
            'This is a security risk — the URL should be randomized.',
        )

    def test_admin_url_is_not_predictable(self):
        """Admin URL path should not be any common predictable pattern."""
        predictable_paths = [
            '/admin/', '/django-admin/', '/manage/', '/dashboard/',
            '/backend/', '/superadmin/',
        ]
        for path in predictable_paths:
            resp = self.client.get(path)
            # Should all be 404, not 200/302
            self.assertNotIn(
                resp.status_code, (200, 302),
                f'{path} returned {resp.status_code} — admin URL may be predictable',
            )
