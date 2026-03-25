from django.core.management.base import BaseCommand, CommandError

from api.models import User
from api.services.firebase_admin import sync_firebase_auth_user


class Command(BaseCommand):
    help = 'Sync backend employee/admin users to Firebase Authentication accounts.'

    def add_arguments(self, parser):
        parser.add_argument(
            '--role',
            choices=['admin', 'office_staff', 'conductor', 'all'],
            default='all',
            help='Filter users by role (default: all).',
        )
        parser.add_argument(
            '--set-password',
            default='',
            help='Optional plain password applied to all selected users (also updates local backend password).',
        )
        parser.add_argument(
            '--active-only',
            action='store_true',
            help='Sync only active users.',
        )

    def handle(self, *args, **options):
        role = options['role']
        password = (options['set_password'] or '').strip()
        active_only = bool(options['active_only'])

        queryset = User.objects.order_by('id')
        if role != 'all':
            queryset = queryset.filter(role=role)
        if active_only:
            queryset = queryset.filter(is_active=True)

        synced = 0
        failed = 0
        for user in queryset:
            try:
                if password:
                    user.set_password(password)
                    user.save(update_fields=['password'])

                sync_firebase_auth_user(
                    user,
                    raw_password=password or None,
                    allow_create=True,
                )
                synced += 1
            except Exception as exc:
                failed += 1
                self.stderr.write(
                    self.style.ERROR(f'Failed user id={user.id} phone={user.phone}: {exc}'),
                )

        self.stdout.write(
            self.style.SUCCESS(f'Firebase user sync complete. synced={synced} failed={failed}'),
        )
