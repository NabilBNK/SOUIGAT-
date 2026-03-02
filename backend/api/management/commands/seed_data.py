"""seed_data: Populate dev database with 5 offices, users, buses, and pricing."""
from datetime import date

from django.core.management.base import BaseCommand

from api.models import Office, User, Bus, PricingConfig


class Command(BaseCommand):
    help = 'Seed development database with initial data'

    def handle(self, *args, **options):
        self.stdout.write('Seeding database...\n')

        # --- Offices ---
        offices_data = [
            {'name': 'Algiers Central', 'city': 'Algiers', 'phone': '021000001'},
            {'name': 'Oran Office', 'city': 'Oran', 'phone': '041000001'},
            {'name': 'Constantine Office', 'city': 'Constantine', 'phone': '031000001'},
            {'name': 'Annaba Office', 'city': 'Annaba', 'phone': '038000001'},
            {'name': 'Sétif Office', 'city': 'Sétif', 'phone': '036000001'},
        ]
        offices = {}
        for data in offices_data:
            office, created = Office.objects.get_or_create(name=data['name'], defaults=data)
            offices[data['city']] = office
            status = 'created' if created else 'exists'
            self.stdout.write(f"  Office: {office.name} [{status}]")

        # --- Admin user ---
        if not User.objects.filter(phone='0500000001').exists():
            User.objects.create_superuser(
                phone='0500000001',
                password='admin123',
                first_name='Admin',
                last_name='SOUIGAT',
                role='admin',
            )
            self.stdout.write('  User: Admin (0500000001) [created]')
        else:
            self.stdout.write('  User: Admin (0500000001) [exists]')

        # --- Office staff ---
        staff_data = [
            {'phone': '0600000001', 'first_name': 'Karim', 'last_name': 'Benali',
             'role': 'office_staff', 'department': 'all', 'office': offices['Algiers']},
            {'phone': '0600000002', 'first_name': 'Amina', 'last_name': 'Hadj',
             'role': 'office_staff', 'department': 'all', 'office': offices['Oran']},
            {'phone': '0600000003', 'first_name': 'Youcef', 'last_name': 'Kaci',
             'role': 'office_staff', 'department': 'cargo', 'office': offices['Algiers']},
        ]
        for data in staff_data:
            if not User.objects.filter(phone=data['phone']).exists():
                User.objects.create_user(password='staff123', **data)
                self.stdout.write(f"  User: {data['first_name']} ({data['phone']}) [created]")

        # --- Conductors ---
        conductor_data = [
            {'phone': '0700000001', 'first_name': 'Mohamed', 'last_name': 'Larbi',
             'role': 'conductor', 'office': offices['Algiers']},
            {'phone': '0700000002', 'first_name': 'Rachid', 'last_name': 'Saidi',
             'role': 'conductor', 'office': offices['Oran']},
        ]
        for data in conductor_data:
            if not User.objects.filter(phone=data['phone']).exists():
                User.objects.create_user(password='conductor123', **data)
                self.stdout.write(f"  User: {data['first_name']} ({data['phone']}) [created]")

        # --- Buses ---
        bus_data = [
            {'plate_number': '16-00001-16', 'office': offices['Algiers'], 'capacity': 49},
            {'plate_number': '31-00001-31', 'office': offices['Oran'], 'capacity': 49},
        ]
        for data in bus_data:
            _, created = Bus.objects.get_or_create(plate_number=data['plate_number'], defaults=data)
            status = 'created' if created else 'exists'
            self.stdout.write(f"  Bus: {data['plate_number']} [{status}]")

        # --- Pricing configs ---
        today = date.today()
        routes = [
            (offices['Algiers'], offices['Oran'], 2500, 500, 1000, 2000),
            (offices['Algiers'], offices['Constantine'], 2000, 400, 800, 1500),
            (offices['Algiers'], offices['Annaba'], 2800, 600, 1200, 2200),
            (offices['Oran'], offices['Constantine'], 1800, 350, 700, 1300),
            (offices['Constantine'], offices['Annaba'], 1200, 250, 500, 900),
        ]
        for origin, dest, p_price, cs, cm, cl in routes:
            _, created = PricingConfig.objects.get_or_create(
                origin_office=origin,
                destination_office=dest,
                effective_from=today,
                defaults={
                    'passenger_price': p_price,
                    'cargo_small_price': cs,
                    'cargo_medium_price': cm,
                    'cargo_large_price': cl,
                    'currency': 'DZD',
                },
            )
            status = 'created' if created else 'exists'
            self.stdout.write(f"  Pricing: {origin.city} -> {dest.city} [{status}]")

        self.stdout.write(self.style.SUCCESS('\n[OK] Seed complete!'))
