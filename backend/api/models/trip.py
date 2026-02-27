from django.core.exceptions import ValidationError
from django.db import models
from django.db.models import Q

from .mixins import SoftDeleteManager, TimestampMixin, SoftDeleteMixin


class TripManager(SoftDeleteManager):
    def active(self):
        return self.get_queryset().filter(status__in=['scheduled', 'in_progress'])

    def for_office(self, office):
        return self.get_queryset().filter(
            Q(origin_office=office) | Q(destination_office=office),
        )

    def for_conductor(self, user):
        return self.get_queryset().filter(conductor=user)

    def with_relations(self):
        return self.get_queryset().select_related(
            'origin_office', 'destination_office', 'bus', 'conductor',
        )


class Trip(TimestampMixin, SoftDeleteMixin, models.Model):
    STATUS_CHOICES = [
        ('scheduled', 'Scheduled'),
        ('in_progress', 'In Progress'),
        ('completed', 'Completed'),
        ('cancelled', 'Cancelled'),
    ]

    origin_office = models.ForeignKey(
        'api.Office', on_delete=models.PROTECT, related_name='trips_from',
    )
    destination_office = models.ForeignKey(
        'api.Office', on_delete=models.PROTECT, related_name='trips_to',
    )
    bus = models.ForeignKey(
        'api.Bus', on_delete=models.PROTECT, related_name='trips',
    )
    conductor = models.ForeignKey(
        'api.User', on_delete=models.PROTECT, related_name='conducted_trips',
    )
    departure_datetime = models.DateTimeField()
    arrival_datetime = models.DateTimeField(null=True, blank=True)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='scheduled')

    # Price snapshot — frozen from pricing_config at creation
    passenger_base_price = models.PositiveIntegerField()
    cargo_small_price = models.PositiveIntegerField()
    cargo_medium_price = models.PositiveIntegerField()
    cargo_large_price = models.PositiveIntegerField()
    currency = models.CharField(max_length=3, default='DZD')

    objects = TripManager()

    class Meta:
        db_table = 'trips'
        ordering = ['-departure_datetime']
        indexes = [
            models.Index(
                fields=['origin_office', '-departure_datetime'],
                name='idx_trips_office_date',
            ),
            models.Index(
                fields=['conductor', 'status'],
                name='idx_trips_conductor_status',
                condition=Q(status__in=['scheduled', 'in_progress']),
            ),
        ]

    def clean(self):
        if self.origin_office_id and self.destination_office_id:
            if self.origin_office_id == self.destination_office_id:
                raise ValidationError('Origin and destination must differ.')
        if self.arrival_datetime and self.departure_datetime:
            if self.arrival_datetime <= self.departure_datetime:
                raise ValidationError('Arrival must be after departure.')
        if self.conductor_id:
            from .user import User
            try:
                conductor = User.objects.get(pk=self.conductor_id)
                if conductor.role != 'conductor':
                    raise ValidationError('Assigned user must be a conductor.')
            except User.DoesNotExist:
                pass

    def save(self, *args, **kwargs):
        skip = kwargs.pop('skip_validation', False)
        if not skip:
            self.full_clean()
        super().save(*args, **kwargs)

    def __str__(self):
        return f"Trip #{self.pk}: {self.origin_office} → {self.destination_office}"
