import os
import django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'souigat.settings')
django.setup()

from api.models import Trip, User, Bus

try:
    t = Trip.objects.get(id=42)
    print(f"Trip 42: status={t.status}, conductor={t.conductor_id}, bus={t.bus_id}")
    if t.conductor_id:
        c = User.objects.get(id=t.conductor_id)
        print(f"Conductor: {c.get_full_name()}, role={c.role}")
        print(f"Active trips for conductor: {Trip.objects.filter(conductor=c, status='in_progress').count()}")
    if t.bus_id:
        print(f"Active trips for bus: {Trip.objects.filter(bus_id=t.bus_id, status='in_progress').count()}")
except Exception as e:
    print(f"Error: {e}")
