from rest_framework import serializers
from api.models import PassengerTicket


class PassengerTicketSerializer(serializers.ModelSerializer):
    """Full serializer for passenger tickets."""

    class Meta:
        model = PassengerTicket
        fields = [
            'id', 'trip', 'ticket_number', 'passenger_name',
            'price', 'currency', 'payment_source', 'seat_number',
            'status', 'created_at',
        ]
        read_only_fields = ['ticket_number', 'currency', 'status', 'created_at']
        extra_kwargs = {
            'price': {'required': False}
        }


class PassengerTicketListSerializer(serializers.ModelSerializer):
    """Lightweight ticket listing."""

    class Meta:
        model = PassengerTicket
        fields = ['id', 'ticket_number', 'passenger_name', 'price', 'currency', 'status', 'seat_number']
