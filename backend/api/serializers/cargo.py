from rest_framework import serializers
from api.models import CargoTicket


class CargoTicketSerializer(serializers.ModelSerializer):
    """Full serializer for cargo ticket CRUD."""

    trip_destination_office_id = serializers.ReadOnlyField(source='trip.destination_office_id')

    class Meta:
        model = CargoTicket
        fields = [
            'id', 'trip', 'trip_destination_office_id', 'ticket_number', 'sender_name', 'sender_phone',
            'receiver_name', 'receiver_phone', 'cargo_tier', 'description',
            'price', 'currency', 'payment_source', 'status',
            'delivered_at', 'created_at',
        ]
        read_only_fields = [
            'ticket_number', 'price', 'currency', 'status',
            'delivered_at', 'created_at',
        ]


class CargoTransitionSerializer(serializers.Serializer):
    """Input for state machine transitions."""

    new_status = serializers.CharField()
    reason = serializers.CharField(required=False, allow_blank=True, default='')
