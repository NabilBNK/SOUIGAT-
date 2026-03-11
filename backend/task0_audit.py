import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'souigat.settings')
django.setup()

from api.models import AuditLog

try:
    AuditLog.objects.create(
        action='override',
        user=None,
        table_name='api_trip',
        record_id=2,
        old_values={'status': 'in_progress'},
        new_values={'status': 'completed', 'admin_note': 'Forced completion of orphaned trip 2 to unblock conductor via backend debug script. Sync gates passed.'}
    )
    print("Audit log recorded successfully.")
except Exception as e:
    print(f"Could not write audit log: {e}")
