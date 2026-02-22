from rest_framework import serializers

from api.models import QuarantinedSync


class QuarantinedSyncSerializer(serializers.ModelSerializer):
    """Read serializer for quarantine list/detail."""

    class Meta:
        model = QuarantinedSync
        fields = [
            'id', 'conductor', 'trip', 'original_data', 'reason',
            'status', 'reviewed_by', 'reviewed_at', 'review_notes',
            'created_at',
        ]
        read_only_fields = fields


class QuarantineReviewSerializer(serializers.Serializer):
    """Single item review: approve or reject."""
    status = serializers.ChoiceField(choices=['approved', 'rejected'])
    review_notes = serializers.CharField(required=False, default='')


class BulkQuarantineReviewSerializer(serializers.Serializer):
    """Bulk approve/reject up to 200 items."""
    ids = serializers.ListField(
        child=serializers.IntegerField(),
        min_length=1,
        max_length=200,
    )
    action = serializers.ChoiceField(choices=['approve', 'reject'])
    review_notes = serializers.CharField(required=False, default='')
