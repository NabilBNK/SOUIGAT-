from django.core.exceptions import ValidationError
from django.db import models

from .mixins import SoftDeleteManager, SoftDeleteMixin, TimestampMixin


class RouteTemplateManager(SoftDeleteManager):
    def active(self):
        return self.get_queryset().filter(is_active=True)


class RouteTemplate(TimestampMixin, SoftDeleteMixin, models.Model):
    DIRECTION_CHOICES = [
        ("forward", "Forward"),
        ("reverse", "Reverse"),
    ]

    name = models.CharField(max_length=120)
    code = models.CharField(max_length=40, unique=True)
    direction = models.CharField(max_length=16, choices=DIRECTION_CHOICES, default="forward")
    start_office = models.ForeignKey(
        "api.Office", on_delete=models.PROTECT, related_name="route_templates_from"
    )
    end_office = models.ForeignKey(
        "api.Office", on_delete=models.PROTECT, related_name="route_templates_to"
    )
    source_template = models.ForeignKey(
        "self",
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="derived_templates",
    )
    is_active = models.BooleanField(default=True)

    objects = RouteTemplateManager()

    class Meta:
        db_table = "route_templates"
        ordering = ["name", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["start_office", "end_office", "direction"],
                name="uq_route_template_direction",
            ),
        ]

    def clean(self):
        if self.start_office_id and self.end_office_id and self.start_office_id == self.end_office_id:
            raise ValidationError("Template start and end offices must differ.")

    def __str__(self):
        return f"{self.code}: {self.start_office} -> {self.end_office} ({self.direction})"


class RouteTemplateStop(TimestampMixin, models.Model):
    route_template = models.ForeignKey(
        "api.RouteTemplate", on_delete=models.CASCADE, related_name="stops"
    )
    office = models.ForeignKey("api.Office", on_delete=models.PROTECT, related_name="route_template_stops")
    stop_order = models.PositiveIntegerField()

    class Meta:
        db_table = "route_template_stops"
        ordering = ["route_template_id", "stop_order"]
        constraints = [
            models.UniqueConstraint(
                fields=["route_template", "stop_order"],
                name="uq_route_template_stop_order",
            ),
            models.UniqueConstraint(
                fields=["route_template", "office"],
                name="uq_route_template_stop_office",
            ),
        ]

    def __str__(self):
        return f"{self.route_template_id}#{self.stop_order}:{self.office_id}"


class RouteTemplateSegmentTariff(TimestampMixin, models.Model):
    route_template = models.ForeignKey(
        "api.RouteTemplate", on_delete=models.CASCADE, related_name="segment_tariffs"
    )
    from_stop = models.ForeignKey(
        "api.RouteTemplateStop",
        on_delete=models.CASCADE,
        related_name="segment_tariffs_from",
    )
    to_stop = models.ForeignKey(
        "api.RouteTemplateStop",
        on_delete=models.CASCADE,
        related_name="segment_tariffs_to",
    )
    passenger_price = models.PositiveIntegerField()
    currency = models.CharField(max_length=3, default="DZD")
    is_active = models.BooleanField(default=True)

    class Meta:
        db_table = "route_template_segment_tariffs"
        ordering = ["route_template_id", "from_stop_id"]
        constraints = [
            models.UniqueConstraint(
                fields=["route_template", "from_stop", "to_stop"],
                name="uq_route_template_segment_pair",
            ),
        ]

    def clean(self):
        if self.from_stop_id and self.to_stop_id and self.from_stop_id == self.to_stop_id:
            raise ValidationError("Segment from_stop and to_stop must differ.")

        if self.from_stop_id and self.to_stop_id:
            from_template = self.from_stop.route_template_id
            to_template = self.to_stop.route_template_id
            if from_template != to_template:
                raise ValidationError("Segment stops must belong to the same route template.")

            if self.route_template_id and from_template != self.route_template_id:
                raise ValidationError("Segment route_template must match stop route_template.")

            if self.to_stop.stop_order != self.from_stop.stop_order + 1:
                raise ValidationError("Segment must connect adjacent ordered stops.")

    def __str__(self):
        return f"{self.route_template_id}:{self.from_stop.stop_order}->{self.to_stop.stop_order}"
