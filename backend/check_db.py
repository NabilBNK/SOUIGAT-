
import os
import django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'souigat.settings')
django.setup()
from api.models import Office, Bus, User, AuditLog
print('--- AUDIT LOG ---')
for log in AuditLog.objects.order_by('-created_at')[:5]:
    print(f'{log.created_at} - {log.action} on {log.table_name}: {log.new_values}')

print('\n--- OFFICES ---')
for o in Office.objects.all():
    print(o.id, o.name, o.is_active, o.is_deleted)

print('\n--- ALL OFFICES (inc deleted) ---')
for o in Office.all_objects.all():
    print(o.id, o.name, o.is_active, o.is_deleted)

