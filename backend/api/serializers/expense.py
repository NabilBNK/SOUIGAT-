from rest_framework import serializers
from api.models import TripExpense


class TripExpenseSerializer(serializers.ModelSerializer):
    """Serializer for Trip Expenses."""

    created_by_name = serializers.CharField(
        source='created_by.get_full_name', read_only=True,
    )

    class Meta:
        model = TripExpense
        fields = [
            'id', 'trip', 'description', 'amount', 'currency', 'category',
            'created_by_name', 'created_at',
        ]
        read_only_fields = ['currency', 'created_at']
