from django.contrib.auth.hashers import make_password
from django.db import transaction
from rest_framework import serializers

from api.models import AuditLog, Bus, Office, PricingConfig, User
from api.services.firebase_admin import schedule_firebase_auth_user_sync


class UserManagementSerializer(serializers.ModelSerializer):
    """Admin serializer for user CRUD. Password is write-only."""

    password = serializers.CharField(write_only=True, required=False)

    class Meta:
        model = User
        fields = [
            'id', 'phone', 'first_name', 'last_name',
            'role', 'department', 'office', 'is_active',
            'password', 'device_id', 'device_bound_at',
            'date_joined', 'last_login',
        ]
        read_only_fields = [
            'device_id', 'device_bound_at', 'date_joined', 'last_login',
        ]

    def create(self, validated_data):
        with transaction.atomic():
            password = validated_data.pop('password', None)
            if not password:
                raise serializers.ValidationError({'password': 'Required for new users.'})
            validated_data['password'] = make_password(password)
            user = super().create(validated_data)

            transaction.on_commit(
                lambda: schedule_firebase_auth_user_sync(
                    user_id=user.id,
                    raw_password=password,
                    allow_create=True,
                )
            )

            return user

    def update(self, instance, validated_data):
        with transaction.atomic():
            password = validated_data.pop('password', None)
            if password:
                validated_data['password'] = make_password(password)

            user = super().update(instance, validated_data)

            transaction.on_commit(
                lambda: schedule_firebase_auth_user_sync(
                    user_id=user.id,
                    raw_password=password,
                    allow_create=True,
                )
            )

            return user


class BusManagementSerializer(serializers.ModelSerializer):
    office_name = serializers.CharField(source='office.name', read_only=True)

    class Meta:
        model = Bus
        fields = [
            'id', 'plate_number', 'office', 'office_name',
            'capacity', 'is_active',
        ]


class OfficeManagementSerializer(serializers.ModelSerializer):
    staff_count = serializers.SerializerMethodField()

    class Meta:
        model = Office
        fields = [
            'id', 'name', 'city', 'address', 'phone', 'is_active',
            'staff_count',
        ]

    def get_staff_count(self, obj):
        return obj.staff.count()


class PricingManagementSerializer(serializers.ModelSerializer):
    origin_name = serializers.CharField(source='origin_office.name', read_only=True)
    destination_name = serializers.CharField(source='destination_office.name', read_only=True)

    class Meta:
        model = PricingConfig
        fields = [
            'id', 'origin_office', 'destination_office',
            'origin_name', 'destination_name',
            'passenger_price', 'cargo_small_price',
            'cargo_medium_price', 'cargo_large_price',
            'currency', 'effective_from', 'effective_until',
            'is_active',
        ]

    def validate(self, data):
        instance = self.instance
        origin = data.get('origin_office', getattr(instance, 'origin_office', None))
        dest = data.get('destination_office', getattr(instance, 'destination_office', None))
        if origin and dest and origin == dest:
            raise serializers.ValidationError('Origin and destination must differ.')
        return data


class AuditLogSerializer(serializers.ModelSerializer):
    user_display = serializers.SerializerMethodField()

    class Meta:
        model = AuditLog
        fields = [
            'id', 'user', 'user_display', 'action',
            'table_name', 'record_id',
            'old_values', 'new_values',
            'ip_address', 'created_at',
        ]
        read_only_fields = fields

    def get_user_display(self, obj):
        if obj.user:
            return obj.user.get_full_name()
        return None
