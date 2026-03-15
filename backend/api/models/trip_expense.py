from django.db import models

from .mixins import TimestampMixin


class TripExpense(TimestampMixin, models.Model):
    trip = models.ForeignKey(
        'api.Trip', on_delete=models.CASCADE, related_name='expenses',
    )
    description = models.CharField(max_length=200)
    amount = models.PositiveIntegerField()
    currency = models.CharField(max_length=3, default='DZD')
    synced_at = models.DateTimeField(null=True, blank=True)
    created_by = models.ForeignKey(
        'api.User', on_delete=models.PROTECT, related_name='created_expenses',
    )

    class Meta:
        db_table = 'trip_expenses'
        indexes = [
            models.Index(fields=['trip'], name='idx_expense_trip'),
        ]

    def __str__(self):
        return f"{self.description}: {self.amount} {self.currency}"
