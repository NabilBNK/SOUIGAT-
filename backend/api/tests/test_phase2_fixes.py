from datetime import timedelta
from django.utils import timezone
from rest_framework.test import APITestCase
from rest_framework import status
from api.models import Office, User, Bus, PricingConfig, Trip, PassengerTicket

class Phase2IntegrationTests(APITestCase):
    def setUp(self):
        self.office_a = Office.objects.create(name='Office A', city='Algiers')
        self.office_b = Office.objects.create(name='Office B', city='Oran')
        
        self.admin = User.objects.create_user(phone='0500000099', password='pw', role='admin')
        self.staff_a = User.objects.create_user(phone='0600000099', password='pw', role='office_staff', office=self.office_a)
        self.cond_1 = User.objects.create_user(phone='0700000099', password='pw', role='conductor')
        
        self.bus_1 = Bus.objects.create(plate_number='B1', office=self.office_a, capacity=50)
        
        PricingConfig.objects.create(
            origin_office=self.office_a, destination_office=self.office_b,
            passenger_price=1000, cargo_small_price=500, cargo_medium_price=1000, cargo_large_price=2000,
            effective_from=timezone.now().date()
        )
        
        self.client.force_authenticate(user=self.staff_a)

    def test_bus_double_booking_blocked(self):
        # Create a trip
        now = timezone.now()
        res1 = self.client.post('/api/trips/', {
            'origin_office': self.office_a.id,
            'destination_office': self.office_b.id,
            'bus': self.bus_1.id,
            'conductor': self.cond_1.id,
            'departure_datetime': (now + timedelta(hours=1)).isoformat(),
        })
        self.assertEqual(res1.status_code, status.HTTP_201_CREATED)
        
        # Try to schedule the same bus 2 hours later (within the 8-hour window)
        res2 = self.client.post('/api/trips/', {
            'origin_office': self.office_a.id,
            'destination_office': self.office_b.id,
            'bus': self.bus_1.id,
            'conductor': self.cond_1.id,
            'departure_datetime': (now + timedelta(hours=3)).isoformat(),
        })
        # Should fail due to overlap window in perform_create
        self.assertEqual(res2.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn('bus', res2.data)

    def test_conductor_double_booking_blocked(self):
        # Create a trip
        now = timezone.now()
        res1 = self.client.post('/api/trips/', {
            'origin_office': self.office_a.id,
            'destination_office': self.office_b.id,
            'bus': self.bus_1.id,
            'conductor': self.cond_1.id,
            'departure_datetime': (now + timedelta(hours=1)).isoformat(),
        })
        self.assertEqual(res1.status_code, status.HTTP_201_CREATED)
        
        # Try to schedule the same conductor 2 hours later (within 4h window) on a DIFFERENT bus
        bus_2 = Bus.objects.create(plate_number='B2', office=self.office_a, capacity=50)
        res2 = self.client.post('/api/trips/', {
            'origin_office': self.office_a.id,
            'destination_office': self.office_b.id,
            'bus': bus_2.id,
            'conductor': self.cond_1.id,
            'departure_datetime': (now + timedelta(hours=3)).isoformat(),
        })
        self.assertEqual(res2.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn('conductor', res2.data)

    def test_role_based_status_transitions(self):
        # Create a trip
        now = timezone.now()
        trip_res = self.client.post('/api/trips/', {
            'origin_office': self.office_a.id,
            'destination_office': self.office_b.id,
            'bus': self.bus_1.id,
            'conductor': self.cond_1.id,
            'departure_datetime': (now + timedelta(minutes=15)).isoformat(),
        })
        trip_id = trip_res.data['id']

        # Office staff tries to start the trip — should be blocked (only conductor/admin can start)
        res_start_staff = self.client.post(f'/api/trips/{trip_id}/start/')
        self.assertEqual(res_start_staff.status_code, status.HTTP_403_FORBIDDEN)

        # Conductor starts the trip
        self.client.force_authenticate(user=self.cond_1)
        res_start_cond = self.client.post(f'/api/trips/{trip_id}/start/')
        self.assertEqual(res_start_cond.status_code, status.HTTP_200_OK)
        self.assertEqual(res_start_cond.data['status'], 'in_progress')

        # Office staff tries to complete it — should be blocked
        self.client.force_authenticate(user=self.staff_a)
        res_comp_staff = self.client.post(f'/api/trips/{trip_id}/complete/')
        self.assertEqual(res_comp_staff.status_code, status.HTTP_403_FORBIDDEN)

        # Conductor completes it
        self.client.force_authenticate(user=self.cond_1)
        res_comp_cond = self.client.post(f'/api/trips/{trip_id}/complete/')
        self.assertEqual(res_comp_cond.status_code, status.HTTP_200_OK)
        self.assertEqual(res_comp_cond.data['status'], 'completed')

    def test_start_trip_any_time_allowed(self):
        # Create a trip 2 hours in the future
        now = timezone.now()
        trip_res = self.client.post('/api/trips/', {
            'origin_office': self.office_a.id,
            'destination_office': self.office_b.id,
            'bus': self.bus_1.id,
            'conductor': self.cond_1.id,
            'departure_datetime': (now + timedelta(hours=2)).isoformat(),
        })
        trip_id = trip_res.data['id']

        # Conductor can start it immediately regardless of departure time.
        self.client.force_authenticate(user=self.cond_1)
        res = self.client.post(f'/api/trips/{trip_id}/start/')
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        self.assertEqual(res.data['status'], 'in_progress')

    def test_ticket_price_floor_enforced(self):
        # Create a trip first
        now = timezone.now()
        trip_res = self.client.post('/api/trips/', {
            'origin_office': self.office_a.id,
            'destination_office': self.office_b.id,
            'bus': self.bus_1.id,
            'conductor': self.cond_1.id,
            'departure_datetime': (now + timedelta(hours=1)).isoformat(),
        })
        self.assertEqual(trip_res.status_code, status.HTTP_201_CREATED)
        trip_id = trip_res.data['id']

        # Try to create a ticket with price < 100 DA
        res1 = self.client.post('/api/tickets/', {
            'trip': trip_id,
            'passenger_name': 'Test Passager',
            'price': 50,
            'payment_source': 'cash'
        })
        self.assertEqual(res1.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn('price', res1.data)

        # Ticket with valid price >= 100 DA
        res2 = self.client.post('/api/tickets/', {
            'trip': trip_id,
            'passenger_name': 'Test Passager',
            'price': 150,
            'payment_source': 'cash'
        })
        self.assertEqual(res2.status_code, status.HTTP_201_CREATED)
        self.assertEqual(res2.data['price'], 150)
