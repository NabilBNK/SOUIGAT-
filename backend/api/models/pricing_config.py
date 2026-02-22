import logging
from datetime import date

from django.core.cache import cache
from django.core.exceptions import ValidationError
from django.db import models
from django.db.models import Q

from .mixins import SoftDeleteManager, TimestampMixin, SoftDeleteMixin

logger = logging.getLogger(__name__)

PRICING_CACHE_TTL = 3600  # 1 hour


class PricingConfigManager(SoftDeleteManager):
    def get_active_pricing(self, origin, destination, for_date=None):
        """Return the active pricing for a route on a given date. Redis-cached."""
        for_date = for_date or date.today()

        origin_id = origin.id if hasattr(origin, 'id') else origin
        dest_id = destination.id if hasattr(destination, 'id') else destination
        cache_key = f'pricing:{origin_id}:{dest_id}:{for_date}'

        cached = cache.get(cache_key)
        if cached is not None:
            return cached

        pricing = self.get_queryset().filter(
            origin_office=origin,
            destination_office=destination,
            effective_from__lte=for_date,
            is_active=True,
        ).filter(
            Q(effective_until__isnull=True) | Q(effective_until__gte=for_date),
        ).order_by('-effective_from').first()

        if pricing:
            pricing_data = {
                'id': pricing.id,
                'passenger_price': pricing.passenger_price,
                'cargo_small_price': pricing.cargo_small_price,
                'cargo_medium_price': pricing.cargo_medium_price,
                'cargo_large_price': pricing.cargo_large_price,
                'currency': pricing.currency,
            }
            cache.set(cache_key, pricing_data, timeout=PRICING_CACHE_TTL)
            return pricing_data

        return None


class PricingConfig(TimestampMixin, SoftDeleteMixin, models.Model):
    origin_office = models.ForeignKey(
        'api.Office', on_delete=models.PROTECT, related_name='pricing_from',
    )
    destination_office = models.ForeignKey(
        'api.Office', on_delete=models.PROTECT, related_name='pricing_to',
    )
    passenger_price = models.PositiveIntegerField()
    cargo_small_price = models.PositiveIntegerField()
    cargo_medium_price = models.PositiveIntegerField()
    cargo_large_price = models.PositiveIntegerField()
    currency = models.CharField(max_length=3, default='DZD')
    effective_from = models.DateField()
    effective_until = models.DateField(null=True, blank=True)
    is_active = models.BooleanField(default=True)

    objects = PricingConfigManager()

    class Meta:
        db_table = 'pricing_config'
        constraints = [
            models.UniqueConstraint(
                fields=['origin_office', 'destination_office', 'effective_from'],
                name='uq_pricing_route_date',
            ),
        ]

    def clean(self):
        if self.origin_office_id and self.destination_office_id:
            if self.origin_office_id == self.destination_office_id:
                raise ValidationError('Origin and destination must differ.')

        # Prevent overlapping date ranges for same route
        end = self.effective_until or date(9999, 12, 31)
        overlaps = PricingConfig.objects.filter(
            origin_office=self.origin_office,
            destination_office=self.destination_office,
            effective_from__lte=end,
            is_active=True,
        ).filter(
            Q(effective_until__isnull=True) | Q(effective_until__gte=self.effective_from),
        ).exclude(pk=self.pk)

        if overlaps.exists():
            raise ValidationError('Date range overlaps with existing pricing.')

    def __str__(self):
        return (
            f"{self.origin_office} → {self.destination_office}: "
            f"{self.passenger_price} {self.currency}"
        )
