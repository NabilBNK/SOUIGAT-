from django.db import models

from .mixins import TimestampMixin


class TripReportSnapshot(TimestampMixin, models.Model):
    """Frozen per-trip per-office aggregates for reporting closed/settled trips."""

    trip = models.ForeignKey(
        'api.Trip', on_delete=models.CASCADE, related_name='report_snapshots',
    )
    office = models.ForeignKey(
        'api.Office', on_delete=models.CASCADE, related_name='trip_report_snapshots',
    )
    report_date = models.DateField()

    total_trips = models.PositiveIntegerField(default=1)
    total_passengers = models.PositiveIntegerField(default=0)
    total_cargo = models.PositiveIntegerField(default=0)
    passenger_revenue = models.PositiveIntegerField(default=0)
    cargo_revenue = models.PositiveIntegerField(default=0)
    expense_total = models.PositiveIntegerField(default=0)
    net_revenue = models.IntegerField(default=0)

    settlement_status = models.CharField(max_length=20, default='pending')
    source_updated_at = models.DateTimeField()

    class Meta:
        db_table = 'trip_report_snapshots'
        constraints = [
            models.UniqueConstraint(
                fields=['trip', 'office'],
                name='uniq_trip_report_snapshot_trip_office',
            ),
        ]
        indexes = [
            models.Index(fields=['office', 'report_date'], name='idx_trip_snapshot_office_day'),
            models.Index(fields=['report_date'], name='idx_trip_snapshot_day'),
            models.Index(fields=['settlement_status'], name='idx_trip_snap_settle_status'),
        ]
