
import os
import django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'souigat.settings')
django.setup()
from api.models import AuditLog
print([(a.id, a.action, a.table_name, a.record_id, a.created_at) for a in AuditLog.objects.order_by('-created_at')[:10]])

