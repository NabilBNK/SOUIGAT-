"""T3: Add database-level CHECK constraints for data integrity."""
from django.db import migrations


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0001_initial'),
    ]

    def _get_operations():
        # Only apply constraints if not using SQLite (local fallback)
        from django.conf import settings
        if 'sqlite' in settings.DATABASES['default']['ENGINE']:
            return []

        return [
            # Price must be positive (defense-in-depth beyond PositiveIntegerField)
            migrations.RunSQL(
                sql="ALTER TABLE passenger_tickets ADD CONSTRAINT pt_price_positive CHECK (price > 0);",
                reverse_sql="ALTER TABLE passenger_tickets DROP CONSTRAINT IF EXISTS pt_price_positive;",
            ),
            migrations.RunSQL(
                sql="ALTER TABLE cargo_tickets ADD CONSTRAINT ct_price_positive CHECK (price > 0);",
                reverse_sql="ALTER TABLE cargo_tickets DROP CONSTRAINT IF EXISTS ct_price_positive;",
            ),
            migrations.RunSQL(
                sql="ALTER TABLE trip_expenses ADD CONSTRAINT te_amount_positive CHECK (amount > 0);",
                reverse_sql="ALTER TABLE trip_expenses DROP CONSTRAINT IF EXISTS te_amount_positive;",
            ),
            # Trip business rules at DB level
            migrations.RunSQL(
                sql=(
                    "ALTER TABLE trips ADD CONSTRAINT trip_arrival_after_departure "
                    "CHECK (arrival_datetime IS NULL OR arrival_datetime > departure_datetime);"
                ),
                reverse_sql="ALTER TABLE trips DROP CONSTRAINT IF EXISTS trip_arrival_after_departure;",
            ),
            migrations.RunSQL(
                sql=(
                    "ALTER TABLE trips ADD CONSTRAINT trip_different_offices "
                    "CHECK (origin_office_id != destination_office_id);"
                ),
                reverse_sql="ALTER TABLE trips DROP CONSTRAINT IF EXISTS trip_different_offices;",
            ),
            # Currency format validation
            migrations.RunSQL(
                sql="ALTER TABLE trips ADD CONSTRAINT trip_currency_format CHECK (currency ~ '^[A-Z]{3}$');",
                reverse_sql="ALTER TABLE trips DROP CONSTRAINT IF EXISTS trip_currency_format;",
            ),
            migrations.RunSQL(
                sql="ALTER TABLE passenger_tickets ADD CONSTRAINT pt_currency_format CHECK (currency ~ '^[A-Z]{3}$');",
                reverse_sql="ALTER TABLE passenger_tickets DROP CONSTRAINT IF EXISTS pt_currency_format;",
            ),
            migrations.RunSQL(
                sql="ALTER TABLE cargo_tickets ADD CONSTRAINT ct_currency_format CHECK (currency ~ '^[A-Z]{3}$');",
                reverse_sql="ALTER TABLE cargo_tickets DROP CONSTRAINT IF EXISTS ct_currency_format;",
            ),
            migrations.RunSQL(
                sql="ALTER TABLE trip_expenses ADD CONSTRAINT te_currency_format CHECK (currency ~ '^[A-Z]{3}$');",
                reverse_sql="ALTER TABLE trip_expenses DROP CONSTRAINT IF EXISTS te_currency_format;",
            ),
            migrations.RunSQL(
                sql="ALTER TABLE pricing_config ADD CONSTRAINT pc_currency_format CHECK (currency ~ '^[A-Z]{3}$');",
                reverse_sql="ALTER TABLE pricing_config DROP CONSTRAINT IF EXISTS pc_currency_format;",
            ),
            # Pricing prices must be positive
            migrations.RunSQL(
                sql="ALTER TABLE pricing_config ADD CONSTRAINT pc_prices_positive CHECK (passenger_price > 0 AND cargo_small_price > 0 AND cargo_medium_price > 0 AND cargo_large_price > 0);",
                reverse_sql="ALTER TABLE pricing_config DROP CONSTRAINT IF EXISTS pc_prices_positive;",
            ),
        ]

    operations = _get_operations()
