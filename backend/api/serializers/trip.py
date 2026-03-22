from rest_framework import serializers
from api.models import Trip, PricingConfig


class TripSerializer(serializers.ModelSerializer):
    """Full serializer for Trip CRUD with pricing snapshot from PricingConfig."""

    conductor_name = serializers.CharField(source='conductor.get_full_name', read_only=True)
    bus_plate = serializers.CharField(source='bus.plate_number', read_only=True)
    origin_name = serializers.CharField(source='origin_office.name', read_only=True)
    destination_name = serializers.CharField(source='destination_office.name', read_only=True)
    origin_office_name = serializers.CharField(source='origin_office.name', read_only=True)
    destination_office_name = serializers.CharField(source='destination_office.name', read_only=True)
    passenger_count = serializers.SerializerMethodField()
    cargo_count = serializers.SerializerMethodField()
    expense_total = serializers.SerializerMethodField()

    class Meta:
        model = Trip
        fields = [
            'id', 'origin_office', 'destination_office', 'conductor', 'bus',
            'departure_datetime', 'arrival_datetime', 'status',
            'passenger_base_price', 'cargo_small_price',
            'cargo_medium_price', 'cargo_large_price',
            'currency', 'conductor_name', 'bus_plate',
            'origin_name', 'destination_name',
            'origin_office_name', 'destination_office_name',
            'passenger_count', 'cargo_count', 'expense_total',
            'created_at', 'updated_at',
        ]
        read_only_fields = [
            'status', 'arrival_datetime', 'currency',
            'passenger_base_price', 'cargo_small_price',
            'cargo_medium_price', 'cargo_large_price',
        ]

    def get_passenger_count(self, obj):
        return getattr(obj, 'passenger_count', 0)

    def get_cargo_count(self, obj):
        return getattr(obj, 'cargo_count', 0)

    def get_expense_total(self, obj):
        return getattr(obj, 'expense_total', 0)

    def create(self, validated_data):
        """Freeze pricing snapshot from active PricingConfig for this route."""
        origin = validated_data['origin_office']
        destination = validated_data['destination_office']

        departure_date = validated_data['departure_datetime'].date()
        pricing = PricingConfig.objects.get_active_pricing(
            origin, destination, for_date=departure_date
        )
        if pricing is None:
            raise serializers.ValidationError(
                {'route': 'No active pricing configuration for this route.'}
            )

        validated_data['passenger_base_price'] = pricing['passenger_price']
        validated_data['cargo_small_price'] = pricing['cargo_small_price']
        validated_data['cargo_medium_price'] = pricing['cargo_medium_price']
        validated_data['cargo_large_price'] = pricing['cargo_large_price']
        validated_data['currency'] = pricing['currency']

        return super().create(validated_data)


class TripListSerializer(serializers.ModelSerializer):
    """Lightweight serializer for trip listings."""

    origin = serializers.CharField(source='origin_office.name', read_only=True)
    destination = serializers.CharField(source='destination_office.name', read_only=True)
    conductor = serializers.CharField(source='conductor.get_full_name', read_only=True)
    plate = serializers.CharField(source='bus.plate_number', read_only=True)
    origin_office_name = serializers.CharField(source='origin_office.name', read_only=True)
    destination_office_name = serializers.CharField(source='destination_office.name', read_only=True)
    conductor_name = serializers.CharField(source='conductor.get_full_name', read_only=True)
    bus_plate = serializers.CharField(source='bus.plate_number', read_only=True)
    passenger_count = serializers.SerializerMethodField()
    cargo_count = serializers.SerializerMethodField()
    expense_total = serializers.SerializerMethodField()

    class Meta:
        model = Trip
        fields = [
            'id', 'origin_office', 'destination_office', 'bus',
            'origin', 'destination', 'conductor', 'plate',
            'origin_office_name', 'destination_office_name',
            'conductor_name', 'bus_plate',
            'departure_datetime', 'status', 'passenger_base_price', 'currency',
            'passenger_count', 'cargo_count', 'expense_total',
            'created_at', 'updated_at',
        ]

    def get_passenger_count(self, obj):
        return getattr(obj, 'passenger_count', 0)

    def get_cargo_count(self, obj):
        return getattr(obj, 'cargo_count', 0)

    def get_expense_total(self, obj):
        return getattr(obj, 'expense_total', 0)
