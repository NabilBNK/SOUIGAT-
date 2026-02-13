from django.db import models

from .mixins import TimestampMixin


class QuarantinedSync(TimestampMixin, models.Model):
    """Rejected sync data held for admin review. Never discarded."""
    STATUS_CHOICES = [
        ('pending', 'Pending'),
        ('approved', 'Approved'),
        ('rejected', 'Rejected'),
    ]

    conductor = models.ForeignKey(
        'api.User', on_delete=models.PROTECT, related_name='quarantined_syncs',
    )
    trip = models.ForeignKey(
        'api.Trip', on_delete=models.PROTECT, related_name='quarantined_syncs',
    )
    original_data = models.JSONField()
    reason = models.TextField()
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='pending')
    reviewed_by = models.ForeignKey(
        'api.User', on_delete=models.SET_NULL,
        null=True, blank=True, related_name='reviewed_quarantines',
    )
    reviewed_at = models.DateTimeField(null=True, blank=True)
    review_notes = models.TextField(blank=True, default='')

    class Meta:
        db_table = 'quarantined_syncs'
        indexes = [
            models.Index(
                fields=['status', '-created_at'],
                name='idx_quarantine_status',
            ),
        ]

    def __str__(self):
        return f"QS #{self.pk} ({self.status}) — Trip #{self.trip_id}"
