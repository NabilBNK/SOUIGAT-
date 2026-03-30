from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0015_route_template_stop_text_or_office'),
    ]

    operations = [
        migrations.AddField(
            model_name='routetemplate',
            name='cargo_large_price',
            field=models.PositiveIntegerField(default=0),
        ),
        migrations.AddField(
            model_name='routetemplate',
            name='cargo_medium_price',
            field=models.PositiveIntegerField(default=0),
        ),
        migrations.AddField(
            model_name='routetemplate',
            name='cargo_small_price',
            field=models.PositiveIntegerField(default=0),
        ),
        migrations.AddField(
            model_name='routetemplate',
            name='currency',
            field=models.CharField(default='DZD', max_length=3),
        ),
    ]
