import logging

from django.db.models.signals import post_delete, post_save
from django.dispatch import receiver

from api.models import CargoTicket, PassengerTicket, Settlement, Trip, TripExpense
from api.services.firebase_mirror import (
    enqueue_instance_delete,
    enqueue_instance_upsert,
    schedule_mirror_event,
)

logger = logging.getLogger(__name__)


def _queue_upsert(instance):
    try:
        event = enqueue_instance_upsert(instance)
        schedule_mirror_event(event.id)
    except Exception:
        logger.exception(
            'Failed to queue Firebase mirror upsert for %s id=%s',
            instance.__class__.__name__,
            getattr(instance, 'pk', None),
        )


def _queue_delete(instance):
    try:
        event = enqueue_instance_delete(instance)
        schedule_mirror_event(event.id)
    except Exception:
        logger.exception(
            'Failed to queue Firebase mirror delete for %s id=%s',
            instance.__class__.__name__,
            getattr(instance, 'pk', None),
        )


@receiver(post_save, sender=Trip)
def queue_trip_mirror_on_save(sender, instance, **kwargs):
    _queue_upsert(instance)


@receiver(post_delete, sender=Trip)
def queue_trip_mirror_on_delete(sender, instance, **kwargs):
    _queue_delete(instance)


@receiver(post_save, sender=PassengerTicket)
def queue_passenger_ticket_mirror_on_save(sender, instance, **kwargs):
    _queue_upsert(instance)


@receiver(post_delete, sender=PassengerTicket)
def queue_passenger_ticket_mirror_on_delete(sender, instance, **kwargs):
    _queue_delete(instance)


@receiver(post_save, sender=CargoTicket)
def queue_cargo_ticket_mirror_on_save(sender, instance, **kwargs):
    _queue_upsert(instance)


@receiver(post_delete, sender=CargoTicket)
def queue_cargo_ticket_mirror_on_delete(sender, instance, **kwargs):
    _queue_delete(instance)


@receiver(post_save, sender=TripExpense)
def queue_trip_expense_mirror_on_save(sender, instance, **kwargs):
    _queue_upsert(instance)


@receiver(post_delete, sender=TripExpense)
def queue_trip_expense_mirror_on_delete(sender, instance, **kwargs):
    _queue_delete(instance)


@receiver(post_save, sender=Settlement)
def queue_settlement_mirror_on_save(sender, instance, **kwargs):
    _queue_upsert(instance)


@receiver(post_delete, sender=Settlement)
def queue_settlement_mirror_on_delete(sender, instance, **kwargs):
    _queue_delete(instance)
