from datetime import date, timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import Bus, Office, PricingConfig, Trip, TripExpense, User


class TripExpenseTests(TestCase):
    """Tests for expense creation, role enforcement, and trip status blocking."""

    def setUp(self):
        self.office = Office.objects.create(name='Office A', city='Algiers')
        self.office_b = Office.objects.create(name='Office B', city='Oran')

        self.conductor = User.objects.create_user(
            phone='0550000001', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office,
        )
        self.conductor_b = User.objects.create_user(
            phone='0550000002', password='pass',
            first_name='Cond', last_name='B',
            role='conductor', office=self.office,
        )
        self.staff = User.objects.create_user(
            phone='0550000003', password='pass',
            first_name='Staff', last_name='A',
            role='office_staff', office=self.office,
        )

        self.bus = Bus.objects.create(
            plate_number='00001-116-16', capacity=50, office=self.office,
        )

        self.trip = Trip.objects.create(
            origin_office=self.office,
            destination_office=self.office_b,
            conductor=self.conductor,
            bus=self.bus,
            departure_datetime=timezone.now() + timedelta(hours=1),
            status='in_progress',
            passenger_base_price=1000,
            cargo_small_price=500,
            cargo_medium_price=1000,
            cargo_large_price=1500,
        )

        self.client = APIClient()

    def test_create_expense_success(self):
        self.client.force_authenticate(self.conductor)
        resp = self.client.post('/api/expenses/', {
            'trip': self.trip.id,
            'description': 'Fuel',
            'amount': 5000,
        })
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED)
        self.assertEqual(TripExpense.objects.count(), 1)
        expense = TripExpense.objects.first()
        self.assertEqual(expense.created_by, self.conductor)
        self.assertEqual(expense.currency, 'DZD')

    def test_wrong_conductor_forbidden(self):
        self.client.force_authenticate(self.conductor_b)
        resp = self.client.post('/api/expenses/', {
            'trip': self.trip.id,
            'description': 'Fuel',
            'amount': 5000,
        })
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)

    def test_staff_cannot_create_expenses(self):
        self.client.force_authenticate(self.staff)
        resp = self.client.post('/api/expenses/', {
            'trip': self.trip.id,
            'description': 'Fuel',
            'amount': 5000,
        })
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)

    def test_completed_trip_blocks_expense(self):
        self.trip.status = 'completed'
        self.trip.save(skip_validation=True)
        self.client.force_authenticate(self.conductor)
        resp = self.client.post('/api/expenses/', {
            'trip': self.trip.id,
            'description': 'Late fuel',
            'amount': 3000,
        })
        self.assertIn(resp.status_code, (
            status.HTTP_400_BAD_REQUEST,
            status.HTTP_403_FORBIDDEN,
        ))
