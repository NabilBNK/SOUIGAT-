import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'souigat.settings')
django.setup()

from api.models import User, Trip

trips = Trip.objects.all().select_related('conductor', 'origin_office', 'destination_office')
if not trips:
    print("No trips found in the entire database!")
else:
    print("All Trips found:")
    for t in trips:
        print(f"Trip #{t.id} - Status: {t.status} - Conductor Email: {t.conductor.email} - Route: {t.origin_office.name} -> {t.destination_office.name}")
