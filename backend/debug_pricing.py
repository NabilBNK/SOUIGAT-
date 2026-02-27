
import os
import django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'souigat.settings')
django.setup()
from rest_framework.test import APIClient
from api.models import User, Office
c = APIClient()
user = User.objects.get(phone='0500000001')
c.force_authenticate(user=user)
data = {
    'origin_office': Office.objects.first().id,
    'destination_office': Office.objects.last().id,
    'passenger_price': 1000,
    'cargo_small_price': 200,
    'cargo_medium_price': 500,
    'cargo_large_price': 1500,
    'currency': 'DZD',
    'effective_from': '2026-02-23'
}
res = c.post('/api/admin/pricing/', data, format='json')
print('HTTP', res.status_code)
print('Response:', res.content.decode('utf-8'))

