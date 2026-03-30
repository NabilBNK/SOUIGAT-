from django.core.management.base import BaseCommand, CommandError

from api.models import CargoTicket, PassengerTicket, PricingConfig, RouteTemplate, Settlement, Trip, TripExpense
from api.services.firebase_mirror import enqueue_instance_upsert
from api.tasks import process_firebase_mirror_event

ENTITY_CHOICES = (
    'all',
    'trip',
    'passenger_ticket',
    'cargo_ticket',
    'trip_expense',
    'settlement',
    'pricing_config',
    'route_template',
)


class Command(BaseCommand):
    help = (
        'Backfill operational records from local DB into Firestore mirror. '
        'Local DB remains primary; Firebase is populated asynchronously or inline.'
    )

    def add_arguments(self, parser):
        parser.add_argument(
            '--entity',
            default='all',
            choices=ENTITY_CHOICES,
            help='Entity to backfill (default: all operational entities).',
        )
        parser.add_argument(
            '--batch-size',
            type=int,
            default=200,
            help='Iteration batch size for queryset .iterator().',
        )
        parser.add_argument(
            '--enqueue-only',
            action='store_true',
            help='Only enqueue Firebase mirror events without applying inline.',
        )
        parser.add_argument(
            '--limit',
            type=int,
            default=0,
            help='Optional cap per entity (0 means no cap).',
        )

    def handle(self, *args, **options):
        batch_size = options['batch_size']
        enqueue_only = options['enqueue_only']
        entity = options['entity']
        limit = options['limit']

        if batch_size <= 0:
            raise CommandError('--batch-size must be greater than zero.')
        if limit < 0:
            raise CommandError('--limit cannot be negative.')

        targets = [entity] if entity != 'all' else [
            'trip',
            'passenger_ticket',
            'cargo_ticket',
            'trip_expense',
            'settlement',
            'pricing_config',
            'route_template',
        ]

        summary = {}
        for target in targets:
            queued, applied = self._backfill_entity(
                target=target,
                batch_size=batch_size,
                enqueue_only=enqueue_only,
                limit=limit,
            )
            summary[target] = {'queued': queued, 'applied': applied}

        self.stdout.write(self.style.SUCCESS('Firebase mirror backfill completed.'))
        for target, info in summary.items():
            self.stdout.write(
                f" - {target}: queued={info['queued']} applied={info['applied']}",
            )

    def _queryset_for_entity(self, target):
        if target == 'trip':
            return Trip.all_objects.select_related('origin_office', 'destination_office', 'conductor', 'bus').order_by('id')
        if target == 'passenger_ticket':
            return PassengerTicket.all_objects.select_related(
                'trip__origin_office',
                'trip__destination_office',
                'trip__conductor',
                'created_by',
            ).order_by('id')
        if target == 'cargo_ticket':
            return CargoTicket.all_objects.select_related(
                'trip__origin_office',
                'trip__destination_office',
                'trip__conductor',
                'created_by',
                'status_override_by',
                'delivered_by',
            ).order_by('id')
        if target == 'trip_expense':
            return TripExpense.objects.select_related(
                'trip__origin_office',
                'trip__destination_office',
                'trip__conductor',
                'created_by',
            ).order_by('id')
        if target == 'settlement':
            return Settlement.objects.select_related(
                'trip__origin_office',
                'trip__destination_office',
                'trip__conductor',
                'office',
                'conductor',
                'settled_by',
            ).order_by('id')
        if target == 'pricing_config':
            return PricingConfig.all_objects.select_related(
                'origin_office',
                'destination_office',
            ).order_by('id')
        if target == 'route_template':
            return RouteTemplate.all_objects.select_related(
                'start_office',
                'end_office',
            ).prefetch_related(
                'stops__office',
                'segment_tariffs__from_stop',
                'segment_tariffs__to_stop',
            ).order_by('id')
        raise CommandError(f'Unsupported entity target: {target}')

    def _backfill_entity(self, target, batch_size, enqueue_only, limit):
        qs = self._queryset_for_entity(target)
        if limit:
            qs = qs[:limit]

        queued = 0
        applied = 0

        for instance in qs.iterator(chunk_size=batch_size):
            event = enqueue_instance_upsert(instance)
            queued += 1
            if not enqueue_only:
                process_firebase_mirror_event(event.id)
                applied += 1

            if queued % batch_size == 0:
                self.stdout.write(
                    f'[{target}] processed {queued} records...',
                )

        return queued, applied
