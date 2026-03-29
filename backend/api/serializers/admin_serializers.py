from django.contrib.auth.hashers import make_password
from django.db import transaction
from rest_framework import serializers

from api.models import (
    AuditLog,
    Bus,
    Office,
    PricingConfig,
    RouteTemplate,
    RouteTemplateSegmentTariff,
    RouteTemplateStop,
    User,
)
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

    def validate(self, attrs):
        role = attrs.get('role', getattr(self.instance, 'role', None))
        if role == 'conductor' and attrs.get('office', None) is not None:
            raise serializers.ValidationError({
                'office': 'Un conducteur ne peut pas etre rattache a une agence.',
            })
        return attrs

    def create(self, validated_data):
        with transaction.atomic():
            if validated_data.get('role') == 'conductor':
                validated_data['office'] = None

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
            next_role = validated_data.get('role', instance.role)
            if next_role == 'conductor':
                validated_data['office'] = None

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
    office_name = serializers.SerializerMethodField()

    class Meta:
        model = Bus
        fields = [
            'id', 'plate_number', 'office', 'office_name',
            'capacity', 'is_active',
        ]

    def get_office_name(self, obj):
        return obj.office.name if obj.office_id and obj.office else None

    def create(self, validated_data):
        validated_data['office'] = None
        return super().create(validated_data)

    def update(self, instance, validated_data):
        validated_data['office'] = None
        return super().update(instance, validated_data)


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


class RouteTemplateStopSerializer(serializers.ModelSerializer):
    office_name = serializers.CharField(source="office.name", read_only=True)

    class Meta:
        model = RouteTemplateStop
        fields = ["id", "route_template", "office", "office_name", "stop_order"]


class RouteTemplateSegmentTariffSerializer(serializers.ModelSerializer):
    from_stop_order = serializers.IntegerField(source="from_stop.stop_order", read_only=True)
    to_stop_order = serializers.IntegerField(source="to_stop.stop_order", read_only=True)

    class Meta:
        model = RouteTemplateSegmentTariff
        fields = [
            "id",
            "route_template",
            "from_stop",
            "to_stop",
            "from_stop_order",
            "to_stop_order",
            "passenger_price",
            "currency",
            "is_active",
        ]

    def validate(self, data):
        route_template = data.get("route_template", getattr(self.instance, "route_template", None))
        from_stop = data.get("from_stop", getattr(self.instance, "from_stop", None))
        to_stop = data.get("to_stop", getattr(self.instance, "to_stop", None))

        if route_template and from_stop and from_stop.route_template_id != route_template.id:
            raise serializers.ValidationError("from_stop must belong to route_template.")
        if route_template and to_stop and to_stop.route_template_id != route_template.id:
            raise serializers.ValidationError("to_stop must belong to route_template.")
        if from_stop and to_stop and to_stop.stop_order != from_stop.stop_order + 1:
            raise serializers.ValidationError("Segment tariffs must connect adjacent stops.")
        return data


class RouteTemplateManagementSerializer(serializers.ModelSerializer):
    start_office_name = serializers.CharField(source="start_office.name", read_only=True)
    end_office_name = serializers.CharField(source="end_office.name", read_only=True)
    stops = RouteTemplateStopSerializer(many=True, read_only=True)
    segment_tariffs = RouteTemplateSegmentTariffSerializer(many=True, read_only=True)

    class Meta:
        model = RouteTemplate
        fields = [
            "id",
            "name",
            "code",
            "direction",
            "start_office",
            "start_office_name",
            "end_office",
            "end_office_name",
            "source_template",
            "is_active",
            "stops",
            "segment_tariffs",
        ]

    def validate(self, data):
        instance = self.instance
        start_office = data.get("start_office", getattr(instance, "start_office", None))
        end_office = data.get("end_office", getattr(instance, "end_office", None))
        if start_office and end_office and start_office == end_office:
            raise serializers.ValidationError("Template start and end offices must differ.")
        return data
