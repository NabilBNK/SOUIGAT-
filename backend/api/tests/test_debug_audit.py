"""Debug: What does AuditLog contain after deliver?"""
from datetime import date, timedelta
from django.test import TestCase
from django.utils import timezone
from rest_framework.test import APIClient
from api.models import AuditLog, Bus, CargoTicket, Office, PricingConfig, Trip, User


class DebugAuditTest(TestCase):
    def test_what_audit_log_exists(self):
        office_a = Office.objects.create(name='Algiers', city='Algiers')
        office_b = Office.objects.create(name='Oran', city='Oran')
        staff_b = User.objects.create_user(
            phone='0550000010', password='pass',
            first_name='Staff', last_name='B',
            role='office_staff', office=office_b,
        )
        conductor = User.objects.create_user(
            phone='0550000011', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=office_a,
        )
        bus = Bus.objects.create(plate_number='BUS-DBG', capacity=50, office=office_a)
        PricingConfig.objects.create(
            origin_office=office_a, destination_office=office_b,
            passenger_price=1000, cargo_small_price=500,
            cargo_medium_price=1000, cargo_large_price=1500,
            effective_from=date.today(),
        )
        trip = Trip.objects.create(
            origin_office=office_a, destination_office=office_b,
            conductor=conductor, bus=bus,
            departure_datetime=timezone.now() + timedelta(hours=1),
            status='in_progress',
            passenger_base_price=1000,
            cargo_small_price=500, cargo_medium_price=1000, cargo_large_price=1500,
        )
        cargo = CargoTicket.objects.create(
            trip=trip, ticket_number='CT-DBG-001',
            sender_name='S', receiver_name='R',
            cargo_tier='medium', price=1000,
            payment_source='prepaid', created_by=staff_b,
            status='arrived',
        )

        AuditLog.objects.all().delete()  # Clean slate

        client = APIClient()
        client.force_authenticate(staff_b)
        resp = client.post(f'/api/cargo/{cargo.id}/deliver/')
        print(f"\n\n=== DELIVER RESPONSE: {resp.status_code} ===")
        print(f"=== Response data: {getattr(resp, 'data', None)} ===")

        entries = list(AuditLog.objects.all().values('table_name', 'action', 'record_id', 'old_values'))
        print(f"\n=== ALL AUDIT ENTRIES ({len(entries)}): ===")
        for e in entries:
            print(f"  table={e['table_name']}, action={e['action']}, record_id={e['record_id']}, old_values={e['old_values']}")
        print("=== END ===\n")

        self.assertTrue(len(entries) > 0, 'No audit entries created at all!')
