from rest_framework import serializers

from api.models import SyncLog


class SyncItemSerializer(serializers.Serializer):
    """Single item in a sync batch (ticket, expense, or trip status)."""
    type = serializers.ChoiceField(
        choices=['passenger_ticket', 'cargo_ticket', 'expense', 'trip_status'],
    )
    idempotency_key = serializers.CharField(max_length=100)
    payload = serializers.JSONField()
    local_id = serializers.IntegerField(required=False, default=None)
    # Python int handles 64-bit Long values from Room without overflow


class SyncBatchSerializer(serializers.Serializer):
    """Batch of items from mobile conductor sync."""
    trip_id = serializers.IntegerField()
    items = SyncItemSerializer(many=True)
    resume_from = serializers.IntegerField(default=0, min_value=0)

    def validate_items(self, value):
        if len(value) > 500:
            raise serializers.ValidationError('Batch size cannot exceed 500 items.')
        if not value:
            raise serializers.ValidationError('Batch must contain at least 1 item.')
        return value

    def validate(self, data):
        """Bug #6 fix: cross-field validation for resume_from vs batch length."""
        resume = data.get('resume_from', 0)
        items = data.get('items', [])
        if resume >= len(items):
            raise serializers.ValidationError(
                {'resume_from': 'resume_from must be less than batch size.'}
            )
        return data


class SyncLogResultSerializer(serializers.ModelSerializer):
    """Response for GET /api/sync/log/{key}/ — idempotency key lookup."""
    class Meta:
        model = SyncLog
        fields = ['key', 'accepted', 'quarantined', 'created_at']
