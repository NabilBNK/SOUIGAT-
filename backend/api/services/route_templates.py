from dataclasses import dataclass
from typing import Any

from django.core.exceptions import ValidationError
from django.db import transaction
from django.utils import timezone

from api.models import RouteTemplate, RouteTemplateSegmentTariff, RouteTemplateStop
from api.services.firebase_admin import FirebaseConfigurationError, get_firebase_app

try:
    from firebase_admin import firestore
except ModuleNotFoundError:  # pragma: no cover
    firestore = None


def stop_label(stop: RouteTemplateStop) -> str:
    if stop.office_id and stop.office:
        return stop.office.name
    return (stop.stop_name or "").strip()


@dataclass(frozen=True)
class RouteSnapshot:
    stops: list[dict[str, Any]]
    segment_tariffs: list[dict[str, Any]]
    passenger_base_price: int
    currency: str


def build_route_snapshot(route_template: RouteTemplate) -> RouteSnapshot:
    stops_qs = route_template.stops.select_related("office").order_by("stop_order")
    stops = list(stops_qs)
    if len(stops) < 2:
        raise ValidationError("Route template must contain at least two ordered stops.")

    stop_orders = {stop.stop_order for stop in stops}
    if len(stop_orders) != len(stops):
        raise ValidationError("Route template stop order must be unique.")

    for idx, stop in enumerate(stops):
        expected = idx + 1
        if stop.stop_order != expected:
            raise ValidationError("Route template stops must be contiguous starting at 1.")

    tariffs_qs = route_template.segment_tariffs.select_related("from_stop", "to_stop").filter(is_active=True)
    tariffs = list(tariffs_qs)

    tariff_by_pair: dict[tuple[int, int], RouteTemplateSegmentTariff] = {}
    for tariff in tariffs:
        if tariff.from_stop.route_template_id != route_template.id or tariff.to_stop.route_template_id != route_template.id:
            raise ValidationError("Segment tariff stops must belong to the same route template.")
        from_order = tariff.from_stop.stop_order
        to_order = tariff.to_stop.stop_order
        if to_order <= from_order:
            raise ValidationError("Segment tariff to_stop must be after from_stop in route order.")
        tariff_by_pair[(from_order, to_order)] = tariff

    segment_snapshot: list[dict[str, Any]] = []
    currency = route_template.currency or "DZD"
    for (from_order, to_order), tariff in sorted(tariff_by_pair.items(), key=lambda x: (x[0][0], x[0][1])):
        currency = tariff.currency or currency
        segment_snapshot.append(
            {
                "from_stop_order": from_order,
                "to_stop_order": to_order,
                "from_office_id": stops[from_order - 1].office_id,
                "to_office_id": stops[to_order - 1].office_id,
                "from_stop_label": stop_label(stops[from_order - 1]),
                "to_stop_label": stop_label(stops[to_order - 1]),
                "passenger_price": tariff.passenger_price,
                "currency": tariff.currency or currency,
            }
        )

    adjacent_sum = 0
    for idx in range(1, len(stops)):
        pair = (idx, idx + 1)
        tariff = tariff_by_pair.get(pair)
        if tariff is None:
            raise ValidationError(f"Missing active tariff for segment {idx}->{idx+1}.")
        adjacent_sum += tariff.passenger_price

    direct_full = tariff_by_pair.get((1, len(stops)))
    passenger_base_price = direct_full.passenger_price if direct_full else adjacent_sum
    if direct_full and direct_full.currency:
        currency = direct_full.currency

    stop_snapshot = [
        {
            "stop_order": stop.stop_order,
            "office_id": stop.office_id,
            "office_name": stop.office.name if stop.office_id and stop.office else stop_label(stop),
            "stop_name": stop_label(stop),
        }
        for stop in stops
    ]
    return RouteSnapshot(
        stops=stop_snapshot,
        segment_tariffs=segment_snapshot,
        passenger_base_price=passenger_base_price,
        currency=currency,
    )


def compute_forward_passenger_price(
    *,
    route_stop_snapshot: list[dict[str, Any]] | None,
    route_segment_tariff_snapshot: list[dict[str, Any]] | None,
    boarding_point: str,
    alighting_point: str,
) -> int:
    stops = route_stop_snapshot or []
    segments = route_segment_tariff_snapshot or []
    if not stops or not segments:
        raise ValidationError("Trip route snapshot is missing for forward pricing.")

    stop_index_by_name = {}
    for stop in stops:
        order = int(stop.get("stop_order", 0))
        for key_name in ("stop_name", "office_name"):
            label = str(stop.get(key_name, "")).strip().lower()
            if label:
                stop_index_by_name[label] = order
    start_idx = stop_index_by_name.get(boarding_point.strip().lower())
    end_idx = stop_index_by_name.get(alighting_point.strip().lower())
    if not start_idx:
        raise ValidationError({"boarding_point": "Boarding point is not part of this trip route."})
    if not end_idx:
        raise ValidationError({"alighting_point": "Destination is not part of this trip route."})
    if end_idx <= start_idx:
        raise ValidationError({"alighting_point": "Destination must be after boarding stop on this route."})

    segment_map = {
        (int(seg.get("from_stop_order", 0)), int(seg.get("to_stop_order", 0))): int(seg.get("passenger_price", 0))
        for seg in segments
    }

    direct_price = segment_map.get((start_idx, end_idx))
    if direct_price is not None:
        if direct_price <= 0:
            raise ValidationError("Configured direct fare is invalid.")
        return direct_price

    price = 0
    for stop_order in range(start_idx, end_idx):
        seg_price = segment_map.get((stop_order, stop_order + 1))
        if seg_price is None:
            raise ValidationError("Missing direct fare and missing interval segment tariff in trip route snapshot.")
        price += seg_price
    if price <= 0:
        raise ValidationError("Computed passenger fare is invalid.")
    return price


@transaction.atomic
def create_reverse_template(source_template: RouteTemplate) -> RouteTemplate:
    source_stops = list(source_template.stops.select_related("office").order_by("stop_order"))
    if len(source_stops) < 2:
        raise ValidationError("Cannot reverse template with fewer than two stops.")

    existing_reverse = RouteTemplate.objects.filter(
        start_office=source_template.end_office,
        end_office=source_template.start_office,
        direction="reverse",
        is_deleted=False,
    ).first()
    if existing_reverse is not None:
        return existing_reverse

    reverse_code = f"{source_template.code}_REV"
    suffix = 1
    while RouteTemplate.objects.filter(code=reverse_code).exists():
        suffix += 1
        reverse_code = f"{source_template.code}_REV{suffix}"

    reverse_template = RouteTemplate.objects.create(
        name=f"{source_template.name} (Reverse)",
        code=reverse_code,
        direction="reverse",
        start_office=source_template.end_office,
        end_office=source_template.start_office,
        source_template=source_template,
        cargo_small_price=source_template.cargo_small_price,
        cargo_medium_price=source_template.cargo_medium_price,
        cargo_large_price=source_template.cargo_large_price,
        currency=source_template.currency,
        is_active=source_template.is_active,
    )

    reversed_stops = list(reversed(source_stops))
    new_stops: list[RouteTemplateStop] = []
    for idx, stop in enumerate(reversed_stops, start=1):
        new_stops.append(
            RouteTemplateStop.objects.create(
                route_template=reverse_template,
                office=stop.office,
                stop_name=stop.stop_name,
                stop_order=idx,
            )
        )

    source_tariffs = {
        (tariff.from_stop_id, tariff.to_stop_id): tariff
        for tariff in source_template.segment_tariffs.select_related("from_stop", "to_stop")
    }
    old_stop_to_order = {stop.id: stop.stop_order for stop in source_stops}
    new_order_to_stop = {stop.stop_order: stop for stop in new_stops}
    source_order_to_stop = {stop.stop_order: stop for stop in source_stops}

    for idx in range(1, len(new_stops)):
        # New idx->idx+1 corresponds to old (n-idx)->(n-idx+1) reversed direction.
        old_from_order = len(source_stops) - idx + 1
        old_to_order = len(source_stops) - idx
        old_from = source_order_to_stop[old_from_order]
        old_to = source_order_to_stop[old_to_order]
        old_tariff = source_tariffs.get((old_to.id, old_from.id))

        RouteTemplateSegmentTariff.objects.create(
            route_template=reverse_template,
            from_stop=new_order_to_stop[idx],
            to_stop=new_order_to_stop[idx + 1],
            passenger_price=old_tariff.passenger_price if old_tariff else 0,
            currency=old_tariff.currency if old_tariff and old_tariff.currency else "DZD",
            is_active=old_tariff.is_active if old_tariff else False,
        )

    return reverse_template


def sync_route_template_to_firebase(route_template: RouteTemplate) -> dict[str, Any]:
    """Mirror one route template snapshot to Firestore on-demand."""
    if firestore is None:
        raise FirebaseConfigurationError('firebase-admin firestore client is not installed.')

    snapshot = build_route_snapshot(route_template)
    source_updated_at = timezone.now().isoformat()
    payload = {
        'entity': 'route_template',
        'id': route_template.id,
        'name': route_template.name,
        'code': route_template.code,
        'direction': route_template.direction,
        'is_active': route_template.is_active,
        'start_office_id': route_template.start_office_id,
        'start_office_name': route_template.start_office.name if route_template.start_office_id else '',
        'end_office_id': route_template.end_office_id,
        'end_office_name': route_template.end_office.name if route_template.end_office_id else '',
        'source_template_id': route_template.source_template_id,
        'cargo_small_price': route_template.cargo_small_price,
        'cargo_medium_price': route_template.cargo_medium_price,
        'cargo_large_price': route_template.cargo_large_price,
        'stops': snapshot.stops,
        'segment_tariffs': snapshot.segment_tariffs,
        'passenger_base_price': snapshot.passenger_base_price,
        'currency': snapshot.currency,
        'office_scope_ids': [route_template.start_office_id, route_template.end_office_id],
        'source_updated_at': source_updated_at,
        'sync_version': 1,
        'is_deleted': False,
    }

    app = get_firebase_app()
    client = firestore.client(app=app)
    client.collection('route_template_mirror_v1').document(f'rt_{route_template.id}').set(
        {
            **payload,
            'mirrored_at': firestore.SERVER_TIMESTAMP,
        },
        merge=True,
    )
    return payload
