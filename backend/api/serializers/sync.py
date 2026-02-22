from rest_framework import serializers


class SyncItemSerializer(serializers.Serializer):
    """Single item in a sync batch (ticket or expense)."""
    type = serializers.ChoiceField(
        choices=['passenger_ticket', 'cargo_ticket', 'expense'],
    )
    idempotency_key = serializers.CharField(max_length=64)
    payload = serializers.JSONField()


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
