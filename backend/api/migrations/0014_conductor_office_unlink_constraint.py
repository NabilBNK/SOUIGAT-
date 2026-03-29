from django.db import migrations, models
from django.db.models import Q


def unlink_existing_conductor_offices(apps, schema_editor):
    User = apps.get_model('api', 'User')
    User.objects.filter(role='conductor').exclude(office_id=None).update(office_id=None)


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0013_route_templates_and_trip_snapshot'),
    ]

    operations = [
        migrations.RunPython(unlink_existing_conductor_offices, migrations.RunPython.noop),
        migrations.AddConstraint(
            model_name='user',
            constraint=models.CheckConstraint(
                condition=~Q(role='conductor') | Q(office__isnull=True),
                name='users_conductor_office_null',
            ),
        ),
    ]
