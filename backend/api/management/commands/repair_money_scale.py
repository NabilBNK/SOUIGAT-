from django.core.management.base import BaseCommand
from django.db import transaction

from api.models import PassengerTicket, Settlement, TripExpense
from api.services import compute_settlement
from api.services.money_scale import (
    looks_like_legacy_mobile_expense_amount,
    looks_like_legacy_mobile_passenger_price,
)


class Command(BaseCommand):
    help = 'Repair legacy mobile centime-scaled ticket and expense amounts.'

    def add_arguments(self, parser):
        parser.add_argument(
            '--apply',
            action='store_true',
            help='Persist the proposed repairs instead of printing a dry-run preview.',
        )
        parser.add_argument(
            '--trip-id',
            type=int,
            help='Limit the repair scan to a single trip.',
        )

    def handle(self, *args, **options):
        trip_id = options.get('trip_id')
        apply_changes = options['apply']

        passenger_candidates = list(self._get_passenger_candidates(trip_id))
        expense_candidates = list(self._get_expense_candidates(trip_id))

        if not passenger_candidates and not expense_candidates:
            self.stdout.write(self.style.SUCCESS('No suspicious monetary rows found.'))
            return

        self.stdout.write('Suspicious passenger tickets:')
        for ticket in passenger_candidates:
            self.stdout.write(
                f'  ticket#{ticket.id} trip={ticket.trip_id} '
                f'price={ticket.price} -> {ticket.price // 100}'
            )

        self.stdout.write('Suspicious expenses:')
        for expense in expense_candidates:
            self.stdout.write(
                f'  expense#{expense.id} trip={expense.trip_id} '
                f'amount={expense.amount} -> {expense.amount // 100}'
            )

        if not apply_changes:
            self.stdout.write(self.style.WARNING('Dry run only. Re-run with --apply to persist.'))
            return

        affected_trip_ids = {ticket.trip_id for ticket in passenger_candidates}
        affected_trip_ids.update(expense.trip_id for expense in expense_candidates)

        with transaction.atomic():
            for ticket in passenger_candidates:
                ticket.price = ticket.price // 100
                ticket.save(update_fields=['price'])

            for expense in expense_candidates:
                expense.amount = expense.amount // 100
                expense.save(update_fields=['amount'])

            for settlement in Settlement.objects.select_related('trip').filter(trip_id__in=affected_trip_ids):
                computation = compute_settlement(settlement.trip)
                settlement.expected_passenger_cash = computation.expected_passenger_cash
                settlement.expected_cargo_cash = computation.expected_cargo_cash
                settlement.expected_total_cash = computation.expected_total_cash
                settlement.agency_presale_total = computation.agency_presale_total
                settlement.outstanding_cargo_delivery = computation.outstanding_cargo_delivery
                settlement.expenses_to_reimburse = computation.expenses_to_reimburse
                settlement.net_cash_expected = computation.net_cash_expected
                settlement.calculation_snapshot = computation.snapshot
                settlement.save(
                    update_fields=[
                        'expected_passenger_cash',
                        'expected_cargo_cash',
                        'expected_total_cash',
                        'agency_presale_total',
                        'outstanding_cargo_delivery',
                        'expenses_to_reimburse',
                        'net_cash_expected',
                        'calculation_snapshot',
                        'updated_at',
                    ]
                )

        self.stdout.write(
            self.style.SUCCESS(
                f'Repaired {len(passenger_candidates)} passenger tickets and '
                f'{len(expense_candidates)} expenses.'
            )
        )

    def _get_passenger_candidates(self, trip_id):
        queryset = PassengerTicket.objects.select_related('trip', 'created_by').filter(
            created_by__role='conductor',
        )
        if trip_id is not None:
            queryset = queryset.filter(trip_id=trip_id)

        return [
            ticket for ticket in queryset
            if looks_like_legacy_mobile_passenger_price(ticket.price, ticket.trip.passenger_base_price)
        ]

    def _get_expense_candidates(self, trip_id):
        queryset = TripExpense.objects.select_related('created_by').filter(
            created_by__role='conductor',
        )
        if trip_id is not None:
            queryset = queryset.filter(trip_id=trip_id)

        return [
            expense for expense in queryset
            if looks_like_legacy_mobile_expense_amount(expense.amount, expense.description)
        ]
