from django.db import migrations, models


def unlink_bus_and_conductor_office(apps, schema_editor):
    Bus = apps.get_model('api', 'Bus')
    User = apps.get_model('api', 'User')

    Bus.objects.exclude(office_id=None).update(office_id=None)
    User.objects.filter(role='conductor').exclude(office_id=None).update(office_id=None)


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0010_firebasemirrorevent'),
    ]

    operations = [
        migrations.AlterField(
            model_name='bus',
            name='office',
            field=models.ForeignKey(
                blank=True,
                null=True,
                on_delete=models.SET_NULL,
                related_name='buses',
                to='api.office',
            ),
        ),
        migrations.RunPython(unlink_bus_and_conductor_office, migrations.RunPython.noop),
    ]
