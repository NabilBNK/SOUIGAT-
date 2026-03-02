from django.db import models

from .mixins import TimestampMixin, SoftDeleteMixin


class PassengerTicket(TimestampMixin, SoftDeleteMixin, models.Model):
    STATUS_CHOICES = [
        ('active', 'Active'),
        ('cancelled', 'Cancelled'),
        ('refunded', 'Refunded'),
    ]
    PAYMENT_CHOICES = [
        ('cash', 'Cash'),
        ('prepaid', 'Prepaid'),
    ]

    trip = models.ForeignKey(
        'api.Trip', on_delete=models.PROTECT, related_name='passenger_tickets',
    )
    ticket_number = models.CharField(max_length=20)
    passenger_name = models.CharField(max_length=100)
    price = models.PositiveIntegerField()
    currency = models.CharField(max_length=3, default='DZD')
    payment_source = models.CharField(max_length=20, choices=PAYMENT_CHOICES, default='cash')
    boarding_point = models.CharField(max_length=100, blank=True, null=True)
    alighting_point = models.CharField(max_length=100, blank=True, null=True)
    seat_number = models.CharField(max_length=10, blank=True, default='')
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='active')
    created_by = models.ForeignKey(
        'api.User', on_delete=models.PROTECT, related_name='created_passenger_tickets',
    )
    version = models.PositiveIntegerField(default=1)
    synced_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = 'passenger_tickets'
        ordering = ['-created_at']
        constraints = [
            models.UniqueConstraint(
                fields=['trip', 'ticket_number'],
                name='uq_passenger_trip_ticket',
            ),
        ]
        indexes = [
            models.Index(fields=['trip'], name='idx_passenger_trip'),
        ]

    def __str__(self):
        return f"PT-{self.trip_id}-{self.ticket_number}"
