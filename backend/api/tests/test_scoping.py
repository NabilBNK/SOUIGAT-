import pytest
import datetime
from django.utils import timezone
from rest_framework import status

from api.models import Trip, CargoTicket, PassengerTicket, Office, User

pytestmark = pytest.mark.django_db

@pytest.fixture
def office_a(db):
    return Office.objects.create(name='Office A', is_active=True, city='City A', timezone='UTC')

@pytest.fixture
def office_b(db):
    return Office.objects.create(name='Office B', is_active=True, city='City B', timezone='UTC')

@pytest.fixture
def staff_a(db, office_a):
    return User.objects.create_user(phone='1000', password='pw', role='office_staff', department='all', office=office_a, first_name='A', last_name='Staff')

@pytest.fixture
def staff_b(db, office_b):
    return User.objects.create_user(phone='2000', password='pw', role='office_staff', department='all', office=office_b, first_name='B', last_name='Staff')

@pytest.fixture
def admin(db):
    return User.objects.create_superuser(phone='admin', password='pw', first_name='Ad', last_name='Min')

def test_office_scoping_trips_and_tickets(api_client, office_a, office_b, staff_a, staff_b, admin, bus, conductor_user):
    """
    Test that users from Office A cannot view or manipulate data belonging to Office B.
    Admins can view everything.
    """
    
    trip_a = Trip.objects.create(
        bus=bus, origin_office=office_a, destination_office=office_b, conductor=conductor_user,
        departure_datetime=timezone.now() + datetime.timedelta(hours=1),
        status='in_progress',
        passenger_base_price=100, cargo_small_price=50, cargo_medium_price=100, cargo_large_price=200,
        currency='XYZ'
    )

    cargo_a = CargoTicket.objects.create(
        trip=trip_a, ticket_number='CTA-001', sender_name='A', receiver_name='B',
        cargo_tier='small', price=50, currency='XYZ', created_by=staff_a
    )

    trip_b = Trip.objects.create(
        bus=bus, origin_office=office_b, destination_office=None, conductor=conductor_user,
        departure_datetime=timezone.now() + datetime.timedelta(hours=2),
        status='scheduled',
        passenger_base_price=100, cargo_small_price=50, cargo_medium_price=100, cargo_large_price=200,
        currency='XYZ'
    )
    
    cargo_b = CargoTicket.objects.create(
        trip=trip_b, ticket_number='CTB-001', sender_name='B', receiver_name='A',
        cargo_tier='small', price=50, currency='XYZ', created_by=staff_b
    )

    # 1. Staff A logs in
    api_client.force_authenticate(user=staff_a)
    
    # Should see Trip A because they are origin_office
    res = api_client.get('/api/trips/')
    assert res.status_code == 200
    ids = [t['id'] for t in res.data['results']]
    assert trip_a.id in ids
    assert trip_b.id not in ids

    # Cannot transition cargo_b (404 because queryset filtered)
    res = api_client.post(f'/api/cargo/{cargo_b.id}/transition/', data={'new_status': 'loaded'})
    assert res.status_code == status.HTTP_404_NOT_FOUND

    # 2. Staff B logs in
    api_client.force_authenticate(user=staff_b)

    # Should see Trip A (they are destination_office) AND Trip B (they are origin_office)
    res = api_client.get('/api/trips/')
    assert res.status_code == 200
    ids = [t['id'] for t in res.data['results']]
    assert trip_a.id in ids
    assert trip_b.id in ids
    
    # 3. Admin logs in
    api_client.force_authenticate(user=admin)
    res = api_client.get('/api/trips/')
    assert res.status_code == 200
    ids = [t['id'] for t in res.data['results']]
    assert trip_a.id in ids
    assert trip_b.id in ids
