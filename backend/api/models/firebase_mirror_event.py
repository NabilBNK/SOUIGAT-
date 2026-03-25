from django.db import models

from .mixins import TimestampMixin


class FirebaseMirrorEvent(TimestampMixin, models.Model):
    STATUS_PENDING = 'pending'
    STATUS_IN_PROGRESS = 'in_progress'
    STATUS_SYNCED = 'synced'
    STATUS_FAILED = 'failed'
    STATUS_CONFLICT = 'conflict'

    STATUS_CHOICES = [
        (STATUS_PENDING, 'Pending'),
        (STATUS_IN_PROGRESS, 'In Progress'),
        (STATUS_SYNCED, 'Synced'),
        (STATUS_FAILED, 'Failed'),
        (STATUS_CONFLICT, 'Conflict'),
    ]

    OPERATION_UPSERT = 'upsert'
    OPERATION_DELETE = 'delete'
    OPERATION_CHOICES = [
        (OPERATION_UPSERT, 'Upsert'),
        (OPERATION_DELETE, 'Delete'),
    ]

    entity_type = models.CharField(max_length=32)
    entity_id = models.CharField(max_length=64)
    operation = models.CharField(max_length=16, choices=OPERATION_CHOICES)
    op_id = models.CharField(max_length=255, unique=True)

    payload = models.JSONField(default=dict, blank=True)
    source_updated_at = models.DateTimeField(db_index=True)

    status = models.CharField(
        max_length=20,
        choices=STATUS_CHOICES,
        default=STATUS_PENDING,
        db_index=True,
    )
    attempts = models.PositiveIntegerField(default=0)
    max_attempts = models.PositiveIntegerField(default=8)
    next_retry_at = models.DateTimeField(null=True, blank=True, db_index=True)
    last_error = models.TextField(blank=True, default='')
    synced_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = 'firebase_mirror_events'
        indexes = [
            models.Index(fields=['status', 'next_retry_at'], name='idx_fbmirror_status_retry'),
            models.Index(fields=['entity_type', 'entity_id'], name='idx_fbmirror_entity'),
        ]

    def __str__(self):
        return f"{self.entity_type}:{self.entity_id} {self.operation} [{self.status}]"
