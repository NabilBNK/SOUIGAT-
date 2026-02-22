"""
Add report-optimized and audit indexes for Week 4.

Only adds indexes that don't already exist on the models.
Existing single-field indexes: idx_passenger_trip, idx_cargo_trip, idx_expense_trip.

New indexes:
- idx_ticket_trip_status: PassengerTicket(trip, status) for aggregation
- idx_cargo_trip_tier: CargoTicket(trip, cargo_tier) for tier aggregation
- idx_trip_route_date: Trip(origin, destination, -departure) for route reports
- idx_audit_user_time: AuditLog(user, -created_at) for user queries
- idx_audit_table_record: AuditLog(table_name, record_id) for record lookup
"""
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0002_db_constraints'),
    ]

    operations = [
        # Report indexes (compound — not duplicating existing single-field indexes)
        migrations.AddIndex(
            model_name='passengerticket',
            index=models.Index(
                fields=['trip', 'status'],
                name='idx_ticket_trip_status',
            ),
        ),
        migrations.AddIndex(
            model_name='cargoticket',
            index=models.Index(
                fields=['trip', 'cargo_tier'],
                name='idx_cargo_trip_tier',
            ),
        ),
        migrations.AddIndex(
            model_name='trip',
            index=models.Index(
                fields=['origin_office', 'destination_office', '-departure_datetime'],
                name='idx_trip_route_date',
            ),
        ),
        # Audit indexes
        migrations.AddIndex(
            model_name='auditlog',
            index=models.Index(
                fields=['user', '-created_at'],
                name='idx_audit_user_time',
            ),
        ),
        migrations.AddIndex(
            model_name='auditlog',
            index=models.Index(
                fields=['table_name', 'record_id'],
                name='idx_audit_table_record',
            ),
        ),
    ]
