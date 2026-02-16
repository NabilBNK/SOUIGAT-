import logging

from django.contrib.auth import authenticate
from django.core.validators import RegexValidator
from rest_framework import serializers

from api.models import User

logger = logging.getLogger(__name__)


class LoginSerializer(serializers.Serializer):
    phone = serializers.CharField(
        max_length=20,
        validators=[
            RegexValidator(
                regex=r'^0[5-79][0-9]{8}$',
                message='Invalid Algerian phone format. Expected: 05/06/07/09 + 8 digits.',
            )
        ],
    )
    password = serializers.CharField(write_only=True, style={'input_type': 'password'})
    device_id = serializers.CharField(
        max_length=64,
        required=False,
        help_text='Required for mobile platform',
    )
    platform = serializers.ChoiceField(
        choices=['web', 'mobile'],
        default='web',
    )

    def validate(self, attrs):
        phone = attrs['phone']
        password = attrs['password']
        platform = attrs.get('platform', 'web')
        device_id = attrs.get('device_id')

        if platform == 'mobile' and not device_id:
            raise serializers.ValidationError({
                'device_id': 'Required for mobile platform.',
            })

        # Same error for wrong phone or password (prevent enumeration)
        try:
            user = User.objects.get(phone=phone)
        except User.DoesNotExist:
            raise serializers.ValidationError('Invalid credentials.')

        if not user.is_active:
            raise serializers.ValidationError('Invalid credentials.')

        if not user.check_password(password):
            raise serializers.ValidationError('Invalid credentials.')

        if user.role == 'conductor' and platform == 'web':
            raise serializers.ValidationError({
                'platform': 'Conductors must use mobile platform.',
            })

        attrs['user'] = user
        return attrs


class UserProfileSerializer(serializers.ModelSerializer):
    office_name = serializers.CharField(source='office.name', read_only=True, default=None)
    office_city = serializers.CharField(source='office.city', read_only=True, default=None)
    permissions = serializers.SerializerMethodField()

    class Meta:
        model = User
        fields = [
            'id', 'phone', 'first_name', 'last_name',
            'role', 'department', 'office', 'office_name', 'office_city',
            'is_active', 'device_id', 'device_bound_at',
            'last_login', 'date_joined', 'permissions',
        ]
        read_only_fields = fields

    def get_permissions(self, obj):
        from api.permissions import get_user_permissions
        return get_user_permissions(obj)
