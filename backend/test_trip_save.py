import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'souigat.settings')
django.setup()

from api.models import Office, Bus, User, PricingConfig
from api.serializers.trip import TripSerializer
from django.utils import timezone
from pprint import pprint
from rest_framework.exceptions import ValidationError

origin = Office.objects.first()
dest = Office.objects.exclude(id=origin.id).first()
bus = Bus.objects.first()
conductor = User.objects.filter(role='conductor').first()

data = {
    'origin_office': origin.id if origin else None,
    'destination_office': dest.id if dest else None,
    'bus': bus.id if bus else None,
    'conductor': conductor.id if conductor else None,
    'departure_datetime': timezone.now().isoformat()
}
s = TripSerializer(data=data)
if s.is_valid():
    try:
        s.save()
        print('Saved successfully!', s.data)
    except ValidationError as e:
        print('ValidationError on save!')
        pprint(e.detail)
    except Exception as e:
        print('Exception on save!', e)
else:
    print('Invalid!')
    pprint(s.errors)
