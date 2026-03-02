"""Add SQL comments to all money columns for auditor clarity."""
from django.db import migrations


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0003_report_audit_indexes'),
    ]

    def _get_operations():
        from django.conf import settings
        if 'sqlite' in settings.DATABASES['default']['ENGINE']:
            return []

        return [
            migrations.RunSQL(
                sql=[
                    # Passenger tickets
                    "COMMENT ON COLUMN passenger_tickets.price IS "
                    "'Integer in smallest currency unit. DZD: 2500 = 2,500 DZD';",

                    # Cargo tickets
                    "COMMENT ON COLUMN cargo_tickets.price IS "
                    "'Integer in smallest currency unit. DZD: 2500 = 2,500 DZD';",

                    # Trips — price snapshot
                    "COMMENT ON COLUMN trips.passenger_base_price IS "
                    "'Snapshot from pricing_config at trip creation. Immutable after trip starts.';",
                    "COMMENT ON COLUMN trips.cargo_small_price IS "
                    "'Snapshot from pricing_config. Immutable after trip starts.';",
                    "COMMENT ON COLUMN trips.cargo_medium_price IS "
                    "'Snapshot from pricing_config. Immutable after trip starts.';",
                    "COMMENT ON COLUMN trips.cargo_large_price IS "
                    "'Snapshot from pricing_config. Immutable after trip starts.';",

                    # Trip expenses
                    "COMMENT ON COLUMN trip_expenses.amount IS "
                    "'Integer in smallest currency unit.';",

                    # Pricing config
                    "COMMENT ON COLUMN pricing_config.passenger_price IS "
                    "'Active route pricing in smallest currency unit.';",
                    "COMMENT ON COLUMN pricing_config.cargo_small_price IS "
                    "'Active route pricing in smallest currency unit.';",
                    "COMMENT ON COLUMN pricing_config.cargo_medium_price IS "
                    "'Active route pricing in smallest currency unit.';",
                    "COMMENT ON COLUMN pricing_config.cargo_large_price IS "
                    "'Active route pricing in smallest currency unit.';",
                ],
                reverse_sql=[
                    "COMMENT ON COLUMN passenger_tickets.price IS NULL;",
                    "COMMENT ON COLUMN cargo_tickets.price IS NULL;",
                    "COMMENT ON COLUMN trips.passenger_base_price IS NULL;",
                    "COMMENT ON COLUMN trips.cargo_small_price IS NULL;",
                    "COMMENT ON COLUMN trips.cargo_medium_price IS NULL;",
                    "COMMENT ON COLUMN trips.cargo_large_price IS NULL;",
                    "COMMENT ON COLUMN trip_expenses.amount IS NULL;",
                    "COMMENT ON COLUMN pricing_config.passenger_price IS NULL;",
                    "COMMENT ON COLUMN pricing_config.cargo_small_price IS NULL;",
                    "COMMENT ON COLUMN pricing_config.cargo_medium_price IS NULL;",
                    "COMMENT ON COLUMN pricing_config.cargo_large_price IS NULL;",
                ],
            ),
        ]

    operations = _get_operations()
