from django.db import models

from .mixins import TimestampMixin, SoftDeleteMixin


class Office(TimestampMixin, SoftDeleteMixin, models.Model):
    name = models.CharField(max_length=100, unique=True)
    city = models.CharField(max_length=100)
    address = models.TextField(blank=True, default='')
    phone = models.CharField(max_length=20, blank=True, default='')
    is_active = models.BooleanField(default=True)

    class Meta:
        db_table = 'offices'
        ordering = ['name']

    def __str__(self):
        return f"{self.name} ({self.city})"
