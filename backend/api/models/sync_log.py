from django.db import models

from .mixins import TimestampMixin


class SyncLog(TimestampMixin, models.Model):
    """Tracks idempotency keys (content hashes) from mobile sync."""
    key = models.CharField(max_length=100, unique=True)
    conductor = models.ForeignKey(
        'api.User', on_delete=models.PROTECT, related_name='sync_logs',
    )
    trip = models.ForeignKey(
        'api.Trip', on_delete=models.PROTECT, related_name='sync_logs',
    )
    accepted = models.PositiveIntegerField(default=0)
    quarantined = models.PositiveIntegerField(default=0)

    class Meta:
        db_table = 'sync_log'

    def __str__(self):
        return f"Sync {self.key[:12]}… — {self.accepted}✓ {self.quarantined}✗"
