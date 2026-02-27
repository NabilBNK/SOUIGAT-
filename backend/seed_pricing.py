
import os, django
from datetime import date
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'souigat.settings')
django.setup()
from api.models import Office, PricingConfig

offices = list(Office.objects.all()[:3])
if len(offices) >= 2:
    if not PricingConfig.objects.filter(origin_office=offices[0]).exists():
        PricingConfig.objects.create(
            origin_office=offices[0], destination_office=offices[1],
            passenger_price=2000, cargo_small_price=500,
            cargo_medium_price=1000, cargo_large_price=1500,
            effective_from=date(2026, 3, 1)
        )
        print('? Seeded first pricing row.')
    if len(offices) >= 3 and not PricingConfig.objects.filter(origin_office=offices[1]).exists():
        PricingConfig.objects.create(
            origin_office=offices[1], destination_office=offices[2],
            passenger_price=2500, cargo_small_price=600,
            cargo_medium_price=1200, cargo_large_price=1800,
            effective_from=date(2026, 3, 1)
        )
        print('? Seeded second pricing row.')
print('? Seed check complete.')

