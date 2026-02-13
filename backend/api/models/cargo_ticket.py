from django.core.exceptions import ValidationError
from django.db import models

from .mixins import TimestampMixin, SoftDeleteMixin


class CargoTicket(TimestampMixin, SoftDeleteMixin, models.Model):
    STATUS_CHOICES = [
        ('created', 'Created'),
        ('in_transit', 'In Transit'),
        ('arrived', 'Arrived'),
        ('delivered', 'Delivered'),
        ('paid', 'Paid'),
        ('refused', 'Refused'),
        ('lost', 'Lost'),
        ('cancelled', 'Cancelled'),
        ('refunded', 'Refunded'),
    ]
    TIER_CHOICES = [
        ('small', 'Small'),
        ('medium', 'Medium'),
        ('large', 'Large'),
    ]
    PAYMENT_CHOICES = [
        ('prepaid', 'Prepaid'),
        ('pay_on_delivery', 'Pay on Delivery'),
    ]

    # Forward-only transitions for non-admin users
    VALID_TRANSITIONS = {
        'created':    ['in_transit', 'cancelled'],
        'in_transit': ['arrived', 'lost'],
        'arrived':    ['delivered', 'refused'],
        'delivered':  ['paid', 'refunded'],
        'paid':       [],
        'refused':    ['cancelled'],
        'lost':       [],
        'cancelled':  [],
        'refunded':   [],
    }

    # Even admin cannot make these financial-fraud transitions
    FORBIDDEN_ADMIN_OVERRIDES = {
        ('paid', 'created'), ('paid', 'in_transit'),
        ('refunded', 'created'), ('refunded', 'delivered'),
        ('delivered', 'created'), ('lost', 'delivered'),
    }

    trip = models.ForeignKey(
        'api.Trip', on_delete=models.PROTECT, related_name='cargo_tickets',
    )
    ticket_number = models.CharField(max_length=20)
    sender_name = models.CharField(max_length=100)
    sender_phone = models.CharField(max_length=20, blank=True, default='')
    receiver_name = models.CharField(max_length=100)
    receiver_phone = models.CharField(max_length=20, blank=True, default='')
    cargo_tier = models.CharField(max_length=10, choices=TIER_CHOICES)
    description = models.TextField(blank=True, default='')
    price = models.PositiveIntegerField()
    currency = models.CharField(max_length=3, default='DZD')
    payment_source = models.CharField(max_length=20, choices=PAYMENT_CHOICES)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='created')

    # Override tracking
    status_override_reason = models.TextField(blank=True, default='')
    status_override_by = models.ForeignKey(
        'api.User', on_delete=models.SET_NULL,
        null=True, blank=True, related_name='cargo_overrides',
    )

    delivered_at = models.DateTimeField(null=True, blank=True)
    delivered_by = models.ForeignKey(
        'api.User', on_delete=models.SET_NULL,
        null=True, blank=True, related_name='cargo_deliveries',
    )
    created_by = models.ForeignKey(
        'api.User', on_delete=models.PROTECT, related_name='created_cargo_tickets',
    )
    version = models.PositiveIntegerField(default=1)
    synced_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = 'cargo_tickets'
        constraints = [
            models.UniqueConstraint(
                fields=['trip', 'ticket_number'],
                name='uq_cargo_trip_ticket',
            ),
        ]
        indexes = [
            models.Index(fields=['trip'], name='idx_cargo_trip'),
            models.Index(
                fields=['trip', 'status'],
                name='idx_cargo_unpaid',
                condition=~models.Q(status__in=['paid', 'refused', 'cancelled']),
            ),
        ]

    def transition_to(self, new_status, user, reason=None):
        """Enforce state machine transitions. Admin can override with reason."""
        valid_next = self.VALID_TRANSITIONS.get(self.status, [])

        if new_status not in valid_next:
            if user.role != 'admin':
                raise ValidationError(
                    f"Cannot transition from '{self.status}' to '{new_status}'."
                )
            if (self.status, new_status) in self.FORBIDDEN_ADMIN_OVERRIDES:
                raise ValidationError(
                    'This transition is forbidden (financial integrity).'
                )
            if not reason or len(reason.strip()) < 10:
                raise ValidationError(
                    'Override reason must be at least 10 characters.'
                )
            self.status_override_reason = reason
            self.status_override_by = user

        self.status = new_status
        self.version += 1
        self.save(skip_transition_check=True)

    def save(self, *args, **kwargs):
        skip = kwargs.pop('skip_transition_check', False)
        if self.pk and not skip:
            try:
                old = CargoTicket.all_objects.only('status').get(pk=self.pk)
            except CargoTicket.DoesNotExist:
                old = None
            if old and old.status != self.status:
                valid_next = self.VALID_TRANSITIONS.get(old.status, [])
                if self.status not in valid_next:
                    raise ValidationError(
                        f"Invalid transition: '{old.status}' → '{self.status}'. "
                        f"Use transition_to() for state changes."
                    )
        super().save(*args, **kwargs)

    def __str__(self):
        return f"CT-{self.trip_id}-{self.ticket_number} ({self.status})"
