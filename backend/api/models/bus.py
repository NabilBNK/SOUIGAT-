from django.db import models

from .mixins import TimestampMixin, SoftDeleteMixin


class Bus(TimestampMixin, SoftDeleteMixin, models.Model):
    plate_number = models.CharField(max_length=20, unique=True)
    office = models.ForeignKey(
        'api.Office', on_delete=models.SET_NULL,
        null=True, blank=True,
        related_name='buses',
    )
    capacity = models.PositiveIntegerField()
    is_active = models.BooleanField(default=True)

    class Meta:
        db_table = 'buses'
        verbose_name_plural = 'buses'

    def __str__(self):
        return f"{self.plate_number} ({self.office.name if self.office else 'Unassigned'})"
