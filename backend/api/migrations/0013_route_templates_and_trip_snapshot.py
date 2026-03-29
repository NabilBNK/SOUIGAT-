from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ("api", "0012_tripreportsnapshot"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name="RouteTemplate",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("created_at", models.DateTimeField(auto_now_add=True, db_index=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
                ("is_deleted", models.BooleanField(db_index=True, default=False)),
                ("deleted_at", models.DateTimeField(blank=True, null=True)),
                ("name", models.CharField(max_length=120)),
                ("code", models.CharField(max_length=40, unique=True)),
                ("direction", models.CharField(choices=[("forward", "Forward"), ("reverse", "Reverse")], default="forward", max_length=16)),
                ("is_active", models.BooleanField(default=True)),
                (
                    "deleted_by",
                    models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.SET_NULL, related_name="+", to=settings.AUTH_USER_MODEL),
                ),
                (
                    "end_office",
                    models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name="route_templates_to", to="api.office"),
                ),
                (
                    "source_template",
                    models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.SET_NULL, related_name="derived_templates", to="api.routetemplate"),
                ),
                (
                    "start_office",
                    models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name="route_templates_from", to="api.office"),
                ),
            ],
            options={
                "db_table": "route_templates",
                "ordering": ["name", "id"],
                "constraints": [
                    models.UniqueConstraint(fields=("start_office", "end_office", "direction"), name="uq_route_template_direction"),
                ],
            },
        ),
        migrations.CreateModel(
            name="RouteTemplateStop",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("created_at", models.DateTimeField(auto_now_add=True, db_index=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
                ("stop_order", models.PositiveIntegerField()),
                (
                    "office",
                    models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name="route_template_stops", to="api.office"),
                ),
                (
                    "route_template",
                    models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="stops", to="api.routetemplate"),
                ),
            ],
            options={
                "db_table": "route_template_stops",
                "ordering": ["route_template_id", "stop_order"],
                "constraints": [
                    models.UniqueConstraint(fields=("route_template", "stop_order"), name="uq_route_template_stop_order"),
                    models.UniqueConstraint(fields=("route_template", "office"), name="uq_route_template_stop_office"),
                ],
            },
        ),
        migrations.CreateModel(
            name="RouteTemplateSegmentTariff",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("created_at", models.DateTimeField(auto_now_add=True, db_index=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
                ("passenger_price", models.PositiveIntegerField()),
                ("currency", models.CharField(default="DZD", max_length=3)),
                ("is_active", models.BooleanField(default=True)),
                (
                    "from_stop",
                    models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="segment_tariffs_from", to="api.routetemplatestop"),
                ),
                (
                    "route_template",
                    models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="segment_tariffs", to="api.routetemplate"),
                ),
                (
                    "to_stop",
                    models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="segment_tariffs_to", to="api.routetemplatestop"),
                ),
            ],
            options={
                "db_table": "route_template_segment_tariffs",
                "ordering": ["route_template_id", "from_stop_id"],
                "constraints": [
                    models.UniqueConstraint(fields=("route_template", "from_stop", "to_stop"), name="uq_route_template_segment_pair"),
                ],
            },
        ),
        migrations.AddField(
            model_name="trip",
            name="route_segment_tariff_snapshot",
            field=models.JSONField(blank=True, default=list),
        ),
        migrations.AddField(
            model_name="trip",
            name="route_stop_snapshot",
            field=models.JSONField(blank=True, default=list),
        ),
        migrations.AddField(
            model_name="trip",
            name="route_template",
            field=models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.PROTECT, related_name="trips", to="api.routetemplate"),
        ),
        migrations.AddIndex(
            model_name="trip",
            index=models.Index(fields=["route_template"], name="idx_trips_route_template"),
        ),
    ]
