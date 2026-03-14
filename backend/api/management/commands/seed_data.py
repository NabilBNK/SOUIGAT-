"""seed_data: Populate dev database with offices, users, buses, and pricing."""
from datetime import date

from django.core.management.base import BaseCommand

from api.models import Bus, Office, PricingConfig, User


class Command(BaseCommand):
    help = 'Seed development database with deterministic initial data'

    def _upsert_user(self, *, phone, password, defaults, superuser=False):
        if superuser:
            user, created = User.objects.get_or_create(
                phone=phone,
                defaults={
                    **defaults,
                    'is_staff': True,
                    'is_superuser': True,
                    'is_active': True,
                },
            )
        else:
            user, created = User.objects.get_or_create(phone=phone, defaults=defaults)

        changed = []
        for field, expected in defaults.items():
            if getattr(user, field) != expected:
                setattr(user, field, expected)
                changed.append(field)

        if superuser:
            if not user.is_staff:
                user.is_staff = True
                changed.append('is_staff')
            if not user.is_superuser:
                user.is_superuser = True
                changed.append('is_superuser')
            if not user.is_active:
                user.is_active = True
                changed.append('is_active')

        user.set_password(password)
        changed.append('password')
        user.save()

        status = 'created' if created else 'updated'
        self.stdout.write(f"  User: {defaults['first_name']} ({phone}) [{status}]")

    def handle(self, *args, **options):
        self.stdout.write('Seeding database...\n')

        # --- Offices ---
        offices_data = [
            {'name': 'Algiers Central', 'city': 'Algiers', 'phone': '021000001'},
            {'name': 'Oran Office', 'city': 'Oran', 'phone': '041000001'},
            {'name': 'Constantine Office', 'city': 'Constantine', 'phone': '031000001'},
            {'name': 'Annaba Office', 'city': 'Annaba', 'phone': '038000001'},
            {'name': 'Setif Office', 'city': 'Setif', 'phone': '036000001'},
        ]
        offices = {}
        for data in offices_data:
            office, created = Office.objects.get_or_create(name=data['name'], defaults=data)
            offices[data['city']] = office
            status = 'created' if created else 'exists'
            self.stdout.write(f"  Office: {office.name} [{status}]")

        # --- Users (deterministic password reset on every seed run) ---
        self._upsert_user(
            phone='0500000001',
            password='admin123',
            superuser=True,
            defaults={
                'first_name': 'Admin',
                'last_name': 'SOUIGAT',
                'role': 'admin',
                'department': None,
                'office': None,
            },
        )

        staff_data = [
            {
                'phone': '0600000001',
                'password': 'staff123',
                'defaults': {
                    'first_name': 'Karim',
                    'last_name': 'Benali',
                    'role': 'office_staff',
                    'department': 'all',
                    'office': offices['Algiers'],
                    'is_active': True,
                },
            },
            {
                'phone': '0600000002',
                'password': 'staff123',
                'defaults': {
                    'first_name': 'Amina',
                    'last_name': 'Hadj',
                    'role': 'office_staff',
                    'department': 'all',
                    'office': offices['Oran'],
                    'is_active': True,
                },
            },
            {
                'phone': '0600000003',
                'password': 'staff123',
                'defaults': {
                    'first_name': 'Youcef',
                    'last_name': 'Kaci',
                    'role': 'office_staff',
                    'department': 'cargo',
                    'office': offices['Algiers'],
                    'is_active': True,
                },
            },
        ]
        for entry in staff_data:
            self._upsert_user(
                phone=entry['phone'],
                password=entry['password'],
                defaults=entry['defaults'],
            )

        conductor_data = [
            {
                'phone': '0700000001',
                'password': 'conductor123',
                'defaults': {
                    'first_name': 'Mohamed',
                    'last_name': 'Larbi',
                    'role': 'conductor',
                    'department': None,
                    'office': offices['Algiers'],
                    'is_active': True,
                },
            },
            {
                'phone': '0700000002',
                'password': 'conductor123',
                'defaults': {
                    'first_name': 'Rachid',
                    'last_name': 'Saidi',
                    'role': 'conductor',
                    'department': None,
                    'office': offices['Oran'],
                    'is_active': True,
                },
            },
        ]
        for entry in conductor_data:
            self._upsert_user(
                phone=entry['phone'],
                password=entry['password'],
                defaults=entry['defaults'],
            )

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
