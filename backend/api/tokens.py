import hashlib

from rest_framework_simplejwt.exceptions import TokenError
from rest_framework_simplejwt.tokens import RefreshToken


class DeviceBoundRefreshToken(RefreshToken):
    """Refresh token with device binding and RBAC claims."""

    @classmethod
    def for_user_and_device(cls, user, device_id=None, platform='web'):
        token = cls.for_user(user)

        # RBAC claims
        token['role'] = user.role
        token['department'] = user.department or 'all'
        token['office_id'] = user.office_id

        # Device binding
        if device_id:
            token['device_id'] = device_id
            token['device_fingerprint'] = cls._hash_device(device_id)

        # Platform for refresh strategy
        token['platform'] = platform

        return token

    @property
    def access_token(self):
        access = super().access_token

        # Copy claims to access token
        for claim in ['role', 'department', 'office_id', 'device_id', 'device_fingerprint', 'platform']:
            if claim in self.payload:
                access[claim] = self.payload[claim]

        return access

    @staticmethod
    def _hash_device(device_id):
        return hashlib.sha256(device_id.encode()).hexdigest()[:32]

    def verify_device(self, device_id):
        if 'device_fingerprint' not in self.payload:
            return True

        expected = self._hash_device(device_id)
        actual = self.payload.get('device_fingerprint')

        if expected != actual:
            raise TokenError('Token not valid for this device')
        return True
