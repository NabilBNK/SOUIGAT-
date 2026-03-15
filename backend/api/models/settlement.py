from django.db import models

from .mixins import TimestampMixin


class Settlement(TimestampMixin, models.Model):
    STATUS_PENDING = 'pending'
    STATUS_PARTIAL = 'partial'
    STATUS_SETTLED = 'settled'
    STATUS_DISPUTED = 'disputed'

    STATUS_CHOICES = [
        (STATUS_PENDING, 'Pending'),
        (STATUS_PARTIAL, 'Partial'),
        (STATUS_SETTLED, 'Settled'),
        (STATUS_DISPUTED, 'Disputed'),
    ]

    trip = models.OneToOneField(
        'api.Trip', on_delete=models.PROTECT, related_name='settlement',
    )
    office = models.ForeignKey(
        'api.Office', on_delete=models.PROTECT, related_name='settlements',
        help_text='Destination office where cash is handed over.',
    )
    conductor = models.ForeignKey(
        'api.User', on_delete=models.PROTECT, related_name='trip_settlements',
    )
    settled_by = models.ForeignKey(
        'api.User', on_delete=models.SET_NULL,
        null=True, blank=True, related_name='recorded_settlements',
    )

    expected_passenger_cash = models.PositiveIntegerField(default=0)
    expected_cargo_cash = models.PositiveIntegerField(default=0)
    expected_total_cash = models.PositiveIntegerField(default=0)
    agency_presale_total = models.PositiveIntegerField(default=0)
    outstanding_cargo_delivery = models.PositiveIntegerField(default=0)
    expenses_to_reimburse = models.PositiveIntegerField(default=0)
    net_cash_expected = models.IntegerField(default=0)

    actual_cash_received = models.PositiveIntegerField(null=True, blank=True)
    actual_expenses_reimbursed = models.PositiveIntegerField(default=0)
    discrepancy_amount = models.IntegerField(
        null=True, blank=True,
        help_text=(
            '= (actual_cash_received - actual_expenses_reimbursed) - net_cash_expected. '
            'In v1 this combines cash variance and reimbursement variance into one net '
            'reconciliation difference.'
        ),
    )

    status = models.CharField(
        max_length=20, choices=STATUS_CHOICES, default=STATUS_PENDING,
    )
    notes = models.TextField(blank=True, default='')
    dispute_reason = models.TextField(blank=True, default='')
    calculation_snapshot = models.JSONField(default=dict, blank=True)

    settled_at = models.DateTimeField(null=True, blank=True)
    disputed_at = models.DateTimeField(null=True, blank=True)
    resolved_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = 'settlements'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['office', 'status'], name='idx_settlement_office_status'),
            models.Index(fields=['conductor', '-created_at'], name='idx_settlement_conductor_date'),
        ]

    def __str__(self):
        return f"Settlement trip={self.trip_id} status={self.status}"
