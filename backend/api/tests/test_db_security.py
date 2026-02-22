"""Tests for T19: DB security (SQL comments + REVOKE)."""
from django.db import connection
from django.test import TestCase


class DBSecurityTests(TestCase):
    """SQL comments on money columns and audit_log protection."""

    def test_money_columns_have_comments(self):
        """All money columns should have SQL comments after migration 0004."""
        expected_comments = {
            ('passenger_tickets', 'price'),
            ('cargo_tickets', 'price'),
            ('trips', 'passenger_base_price'),
            ('trips', 'cargo_small_price'),
            ('trips', 'cargo_medium_price'),
            ('trips', 'cargo_large_price'),
            ('trip_expenses', 'amount'),
            ('pricing_config', 'passenger_price'),
            ('pricing_config', 'cargo_small_price'),
            ('pricing_config', 'cargo_medium_price'),
            ('pricing_config', 'cargo_large_price'),
        }
        with connection.cursor() as cursor:
            for table, column in expected_comments:
                cursor.execute(
                    "SELECT col_description("
                    "(SELECT oid FROM pg_class WHERE relname = %s), "
                    "(SELECT ordinal_position FROM information_schema.columns "
                    "WHERE table_name = %s AND column_name = %s))",
                    [table, table, column],
                )
                row = cursor.fetchone()
                comment = row[0] if row else None
                self.assertIsNotNone(
                    comment,
                    f'Missing SQL comment on {table}.{column}',
                )

    def test_audit_log_insert_works(self):
        """App user can INSERT into audit_log (basic sanity check)."""
        from api.models import AuditLog
        log = AuditLog.objects.create(
            action='create',
            table_name='test',
            record_id=1,
        )
        self.assertIsNotNone(log.id)
