from django.db import migrations, models
from django.db.models import Q
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0014_conductor_office_unlink_constraint'),
    ]

    operations = [
        migrations.AddField(
            model_name='routetemplatestop',
            name='stop_name',
            field=models.CharField(blank=True, default='', max_length=120),
        ),
        migrations.AlterField(
            model_name='routetemplatestop',
            name='office',
            field=models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.PROTECT, related_name='route_template_stops', to='api.office'),
        ),
        migrations.RemoveConstraint(
            model_name='routetemplatestop',
            name='uq_route_template_stop_office',
        ),
        migrations.AddConstraint(
            model_name='routetemplatestop',
            constraint=models.UniqueConstraint(condition=Q(office__isnull=False), fields=('route_template', 'office'), name='uq_route_template_stop_office'),
        ),
        migrations.AddConstraint(
            model_name='routetemplatestop',
            constraint=models.UniqueConstraint(condition=~Q(stop_name=''), fields=('route_template', 'stop_name'), name='uq_route_template_stop_name'),
        ),
        migrations.AddConstraint(
            model_name='routetemplatestop',
            constraint=models.CheckConstraint(
                condition=(Q(office__isnull=False, stop_name='') | (Q(office__isnull=True) & ~Q(stop_name=''))),
                name='route_template_stop_exactly_one_source',
            ),
        ),
    ]
