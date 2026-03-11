import os
import django
from datetime import timedelta

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'souigat.settings')
os.environ['DJANGO_ALLOWED_HOSTS'] = 'testserver,localhost,127.0.0.1'
django.setup()

from django.utils import timezone
from api.models import User, Office, Bus, PricingConfig, Trip, PassengerTicket, CargoTicket
from rest_framework.test import APIClient

def run_simulation():
    print("--- SOUIGAT WORKFLOW SIMULATION ---")
    
    # Setup base entities
    admin, _ = User.objects.get_or_create(phone='admin_sim', defaults={'role': 'admin', 'password': 'pw', 'first_name': 'Admin'})
    admin.set_password('pw')
    admin.save()
    
    ouargla, _ = Office.objects.get_or_create(name='Ouargla Office', city='Ouargla')
    oran, _ = Office.objects.get_or_create(name='Oran Office', city='Oran')
    
    staff_ouargla, _ = User.objects.get_or_create(phone='staff_ouargla', defaults={'role': 'office_staff', 'office': ouargla, 'first_name': 'Staff'})
    staff_ouargla.set_password('pw')
    staff_ouargla.save()
    
    conductor, _ = User.objects.get_or_create(phone='cond_sim', defaults={'role': 'conductor', 'first_name': 'Conductor'})
    conductor.set_password('pw')
    conductor.save()
    
    bus, _ = Bus.objects.get_or_create(plate_number='SIM-BUS-99', defaults={'office': ouargla, 'capacity': 60}) # Ensure capacity is high enough
    bus.capacity = 60
    bus.save()
    
    PricingConfig.objects.get_or_create(
        origin_office=ouargla, destination_office=oran,
        defaults={'passenger_price': 1500, 'cargo_small_price': 500, 'cargo_medium_price': 1000, 'cargo_large_price': 1500, 'effective_from': timezone.now().date()}
    )
    
    client = APIClient()
    
    print("\n[Admin] Simulating full environment setup... Done.")
    
    print("\n[Office Staff] Scheduling a trip from Ouargla to Oran...")
    client.force_authenticate(user=staff_ouargla)
    
    now = timezone.now()
    trip_res = client.post('/api/trips/', {
        'origin_office': ouargla.id,
        'destination_office': oran.id,
        'bus': bus.id,
        'conductor': conductor.id,
        'departure_datetime': (now + timedelta(hours=1)).isoformat()
    })
    
    if trip_res.status_code != 201:
        print(f"Failed to create trip! Response: {getattr(trip_res, 'data', trip_res.content)}")
        # Terminate any existing trips for this bus that might be interfering
        Trip.objects.filter(bus=bus, status__in=['scheduled', 'in_progress']).update(status='cancelled')
        print("Cleaned up old test trips. Trying again...")
        trip_res = client.post('/api/trips/', {
            'origin_office': ouargla.id,
            'destination_office': oran.id,
            'bus': bus.id,
            'conductor': conductor.id,
            'departure_datetime': (now + timedelta(hours=1)).isoformat()
        })
        if trip_res.status_code != 201:
             print(f"Failed to create trip again! Response: {getattr(trip_res, 'data', trip_res.content)}")
             return

    trip_id = trip_res.data['id']
    print(f"   => Trip #{trip_id} scheduled.")
    
    print(f"\n[Office Staff] Preselling 40 passenger tickets (Full Price: 1500 DA)...")
    for i in range(40):
        res = client.post('/api/tickets/', {
            'trip': trip_id,
            'passenger_name': f'Passager Guichet {i+1}',
            'price': 1500,
            'payment_source': 'cash'
        })
        if res.status_code != 201:
            print(f"   => Failed on ticket {i+1}: {res.data}")
            return
    print("   => 40 presold tickets successfully issued.")
    
    print("\n[Conductor] Starting the trip...")
    client.force_authenticate(user=conductor)
    start_res = client.post(f'/api/trips/{trip_id}/start/')
    if start_res.status_code != 200:
        print(f"Failed to start trip: {start_res.data}")
        return
    print("   => Trip started. Status is now IN_PROGRESS.")
    
    print("\n[Conductor] Boarding intermediate passengers (10 tickets @ 800 DA)...")
    for i in range(10):
        res = client.post('/api/tickets/', {
            'trip': trip_id,
            'passenger_name': f'Intermediate {i+1}',
            'price': 800,
            'payment_source': 'cash'
        })
        if res.status_code != 201:
            print(f"   => Failed on intermediate ticket {i+1}: {res.data}")
            return
    print("   => 10 intermediate tickets successfully issued.")
    
    print("\n[Conductor] Loading 5 cargo items...")
    for i in range(5):
        res = client.post('/api/cargo/', {
            'trip': trip_id,
            'sender_name': f'Sender {i}',
            'receiver_name': f'Receiver {i}',
            'cargo_tier': 'small',
            'price': 500,
            'payment_source': 'prepaid',
            'description': 'Bagages'
        })
        if res.status_code != 201:
            print(f"   => Failed on cargo ticket {i+1}: {res.data}")
            return
    print("   => 5 cargo tickets successfully issued.")
    
    print("\n[System Check] Verifying capacities & totals...")
    pt_count = PassengerTicket.objects.filter(trip_id=trip_id).count()
    ct_count = CargoTicket.objects.filter(trip_id=trip_id).count()
    total_revenue_db = sum(t.price for t in PassengerTicket.objects.filter(trip_id=trip_id)) + \
                       sum(t.price for t in CargoTicket.objects.filter(trip_id=trip_id))
    print(f"   => Total Passengers: {pt_count}")
    print(f"   => Total Cargo: {ct_count}")
    print(f"   => Total Revenue: {total_revenue_db} DA")
    print(f"   => (Breakdown: 40*1500 + 10*800 + 5*500 = 60000 + 8000 + 2500 = 70500 DA)")
    assert total_revenue_db == 70500, "Revenue mismatch!"

    print("\n[Conductor] Arriving at destination and completing the trip...")
    comp_res = client.post(f'/api/trips/{trip_id}/complete/')
    if comp_res.status_code != 200:
        print(f"Failed to complete trip: {comp_res.data}")
        return
    
    final_trip = Trip.objects.get(id=trip_id)
    print(f"   => Trip #{trip_id} status: {final_trip.status}")
    print(f"   => Arrived at: {final_trip.arrival_datetime}")
    print("\n--- SIMULATION SUCCESSFUL ---")


if __name__ == '__main__':
    run_simulation()
