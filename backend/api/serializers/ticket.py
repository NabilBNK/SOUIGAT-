from rest_framework import serializers
from api.models import PassengerTicket


class PassengerTicketSerializer(serializers.ModelSerializer):
    """Full serializer for passenger tickets."""

    created_by_name = serializers.CharField(
        source='created_by.get_full_name', read_only=True,
    )

    class Meta:
        model = PassengerTicket
        fields = [
            'id', 'trip', 'ticket_number', 'passenger_name',
            'price', 'currency', 'payment_source',
            'boarding_point', 'alighting_point', 'seat_number',
            'status', 'created_by', 'created_by_name',
            'version', 'synced_at', 'created_at', 'updated_at',
        ]
        read_only_fields = [
            'ticket_number', 'currency', 'status',
            'created_by', 'created_by_name',
            'version', 'synced_at', 'created_at', 'updated_at',
        ]
        extra_kwargs = {
            'price': {'required': False},
            'boarding_point': {'required': False, 'allow_blank': True, 'allow_null': True},
            'alighting_point': {'required': False, 'allow_blank': True, 'allow_null': True},
        }


class PassengerTicketListSerializer(serializers.ModelSerializer):
    """Lightweight ticket listing."""

    created_by_name = serializers.CharField(
        source='created_by.get_full_name', read_only=True,
    )

    class Meta:
        model = PassengerTicket
        fields = [
            'id', 'trip', 'ticket_number', 'passenger_name', 'price', 'currency',
            'payment_source', 'status', 'boarding_point', 'alighting_point',
            'seat_number', 'created_by', 'created_by_name',
            'version', 'synced_at', 'created_at', 'updated_at',
        ]
