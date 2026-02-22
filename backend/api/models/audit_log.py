from django.db import models

from .mixins import TimestampMixin


class AuditLog(TimestampMixin, models.Model):
    """Append-only audit trail. DELETE/UPDATE revoked at DB level (T19)."""
    ACTION_CHOICES = [
        ('create', 'Create'),
        ('update', 'Update'),
        ('delete', 'Delete'),
        ('override', 'Override'),
    ]

    user = models.ForeignKey(
        'api.User', on_delete=models.SET_NULL,
        null=True, blank=True, related_name='audit_logs',
    )
    action = models.CharField(max_length=20, choices=ACTION_CHOICES)
    table_name = models.CharField(max_length=50)
    record_id = models.PositiveIntegerField()
    old_values = models.JSONField(null=True, blank=True)
    new_values = models.JSONField(null=True, blank=True)
    ip_address = models.GenericIPAddressField(null=True, blank=True)

    class Meta:
        db_table = 'audit_log'
        indexes = [
            models.Index(
                fields=['-created_at', 'user'],
                name='idx_audit_timestamp',
            ),
            models.Index(
                fields=['table_name', 'record_id'],
                name='idx_audit_table_record',
            ),
        ]

    def __str__(self):
        return f"[{self.action}] {self.table_name}#{self.record_id}"
