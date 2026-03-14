from rest_framework import serializers
from api.models import PassengerTicket


class PassengerTicketSerializer(serializers.ModelSerializer):
    """Full serializer for passenger tickets."""

    class Meta:
        model = PassengerTicket
        fields = [
            'id', 'trip', 'ticket_number', 'passenger_name',
            'price', 'currency', 'payment_source',
            'boarding_point', 'alighting_point', 'seat_number',
            'status', 'created_at',
        ]
        read_only_fields = ['ticket_number', 'currency', 'status', 'created_at']
        extra_kwargs = {
            'price': {'required': False},
            'boarding_point': {'required': False, 'allow_blank': True, 'allow_null': True},
            'alighting_point': {'required': False, 'allow_blank': True, 'allow_null': True},
        }


class PassengerTicketListSerializer(serializers.ModelSerializer):
    """Lightweight ticket listing."""

    class Meta:
        model = PassengerTicket
        fields = [
            'id', 'ticket_number', 'passenger_name', 'price', 'currency',
            'status', 'boarding_point', 'alighting_point', 'seat_number',
        ]
