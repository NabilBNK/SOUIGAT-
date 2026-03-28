from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0011_unlink_bus_and_conductor_office'),
    ]

    operations = [
        migrations.CreateModel(
            name='TripReportSnapshot',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('created_at', models.DateTimeField(auto_now_add=True, db_index=True)),
                ('updated_at', models.DateTimeField(auto_now=True)),
                ('report_date', models.DateField()),
                ('total_trips', models.PositiveIntegerField(default=1)),
                ('total_passengers', models.PositiveIntegerField(default=0)),
                ('total_cargo', models.PositiveIntegerField(default=0)),
                ('passenger_revenue', models.PositiveIntegerField(default=0)),
                ('cargo_revenue', models.PositiveIntegerField(default=0)),
                ('expense_total', models.PositiveIntegerField(default=0)),
                ('net_revenue', models.IntegerField(default=0)),
                ('settlement_status', models.CharField(default='pending', max_length=20)),
                ('source_updated_at', models.DateTimeField()),
                ('office', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='trip_report_snapshots', to='api.office')),
                ('trip', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='report_snapshots', to='api.trip')),
            ],
            options={
                'db_table': 'trip_report_snapshots',
            },
        ),
        migrations.AddConstraint(
            model_name='tripreportsnapshot',
            constraint=models.UniqueConstraint(fields=('trip', 'office'), name='uniq_trip_report_snapshot_trip_office'),
        ),
        migrations.AddIndex(
            model_name='tripreportsnapshot',
            index=models.Index(fields=['office', 'report_date'], name='idx_trip_snapshot_office_day'),
        ),
        migrations.AddIndex(
            model_name='tripreportsnapshot',
            index=models.Index(fields=['report_date'], name='idx_trip_snapshot_day'),
        ),
        migrations.AddIndex(
            model_name='tripreportsnapshot',
            index=models.Index(fields=['settlement_status'], name='idx_trip_snap_settle_status'),
        ),
    ]
