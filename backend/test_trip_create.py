import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'souigat.settings')
django.setup()

from api.models import Office, Bus, User, PricingConfig
from api.serializers.trip import TripSerializer
from django.utils import timezone
from pprint import pprint

origin = Office.objects.first()
dest = Office.objects.exclude(id=origin.id).first()
bus = Bus.objects.first()
conductor = User.objects.filter(role='conductor').first()

print(f'Origin: {origin}')
print(f'Dest: {dest}')
print(f'Bus: {bus}')
print(f'Conductor: {conductor}')

data = {
    'origin_office': origin.id if origin else None,
    'destination_office': dest.id if dest else None,
    'bus': bus.id if bus else None,
    'conductor': conductor.id if conductor else None,
    'departure_datetime': timezone.now().isoformat()
}
s = TripSerializer(data=data)
if s.is_valid():
    print('Valid!', s.validated_data)
else:
    print('Invalid!')
    pprint(s.errors)
