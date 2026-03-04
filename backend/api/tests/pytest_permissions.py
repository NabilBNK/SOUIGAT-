import pytest
import datetime
from django.utils import timezone
from rest_framework import status

from api.models import Trip, CargoTicket, PassengerTicket

# Pytest markers for DB access
pytestmark = pytest.mark.django_db



def test_guichetier_cannot_access_reports_and_trips(api_client, office_staff_cargo_user):
    """
    Test that a user with department='cargo' gets 403 on sensitive non-cargo endpoints
    that require specific actions, even if they have the 'office_staff' role.
    """
    api_client.force_authenticate(user=office_staff_cargo_user)

    # Scoping endpoints (List/Retrieve) rely on OfficeScopePermission, which might return 200 with empty list
    # or the filtered list. Here we test ACTION endpoints which require explicit MatrixPermissions.

    # 1. POST /api/trips/ (requires 'create_trip')
    response = api_client.post('/api/trips/', data={})
    assert response.status_code == status.HTTP_403_FORBIDDEN

    # 2. POST /api/tickets/ (requires 'create_passenger_ticket')
    response = api_client.post('/api/tickets/', data={})
    assert response.status_code == status.HTTP_403_FORBIDDEN

    # 3. GET /api/reports/daily/ (requires 'view_office_reports')
    response = api_client.get('/api/reports/daily/')
    assert response.status_code == status.HTTP_403_FORBIDDEN

    # 4. POST /api/exports/ (requires 'export_excel')
    response = api_client.post('/api/exports/', data={'report_type': 'daily'})
    assert response.status_code == status.HTTP_403_FORBIDDEN

def test_office_staff_all_can_access_reports_and_trips(api_client, office_staff_user, office):
    """
    Test that a general office staff user (department='all') CAN access these endpoints.
    """
    api_client.force_authenticate(user=office_staff_user)

    # 1. GET /api/reports/daily/
    response = api_client.get('/api/reports/daily/')
    assert response.status_code == status.HTTP_200_OK

    # 2. POST /api/trips/ (will 400 bad request due to missing data, but NOT 403)
    response = api_client.post('/api/trips/', data={})
    assert response.status_code == status.HTTP_400_BAD_REQUEST


def test_admin_can_access_reports_and_trips(api_client, admin_user):
    """
    Test that an admin CAN access these endpoints.
    """
    api_client.force_authenticate(user=admin_user)

    # 1. GET /api/reports/daily/
    response = api_client.get('/api/reports/daily/')
    assert response.status_code == status.HTTP_200_OK

    # 2. POST /api/trips/ (will 400 bad request due to missing data, but NOT 403)
    response = api_client.post('/api/trips/', data={})
    assert response.status_code == status.HTTP_400_BAD_REQUEST

def test_cargo_ticket_transitions(api_client, office_staff_cargo_user, office, bus, conductor_user):
    """
    Verify guichetier can manipulate cargo.
    """
    api_client.force_authenticate(user=office_staff_cargo_user)
    
    # Create trip in progress
    trip = Trip.objects.create(
        bus=bus, origin_office=office, conductor=conductor_user,
        departure_datetime=timezone.now() + datetime.timedelta(hours=1),
        status='in_progress',
        passenger_base_price=100, cargo_small_price=50, cargo_medium_price=100, cargo_large_price=200,
        currency='XYZ'
    )
    
    cargo = CargoTicket.objects.create(
        trip=trip, ticket_number='CTX-001', sender_name='A', receiver_name='B',
        cargo_tier='small', price=50, currency='XYZ', created_by=office_staff_cargo_user
    )

    # Transition allowed
    response = api_client.post(f'/api/cargo/{cargo.id}/transition/', data={'new_status': 'loaded'})
    assert response.status_code == status.HTTP_200_OK
    cargo.refresh_from_db()
    assert cargo.status == 'loaded'
