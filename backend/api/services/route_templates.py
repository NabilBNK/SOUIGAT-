from dataclasses import dataclass
from typing import Any

from django.core.exceptions import ValidationError
from django.db import transaction

from api.models import RouteTemplate, RouteTemplateSegmentTariff, RouteTemplateStop


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

    stop_by_id = {stop.id: stop for stop in stops}
    stop_orders = {stop.stop_order for stop in stops}
    if len(stop_orders) != len(stops):
        raise ValidationError("Route template stop order must be unique.")

    for idx, stop in enumerate(stops):
        expected = idx + 1
        if stop.stop_order != expected:
            raise ValidationError("Route template stops must be contiguous starting at 1.")

    tariffs_qs = route_template.segment_tariffs.select_related("from_stop", "to_stop").filter(is_active=True)
    tariffs = list(tariffs_qs)
    if len(tariffs) != len(stops) - 1:
        raise ValidationError("Route template must define one active adjacent tariff per segment.")

    tariff_by_pair = {}
    for tariff in tariffs:
        if tariff.from_stop.route_template_id != route_template.id or tariff.to_stop.route_template_id != route_template.id:
            raise ValidationError("Segment tariff stops must belong to the same route template.")
        from_order = tariff.from_stop.stop_order
        to_order = tariff.to_stop.stop_order
        if to_order != from_order + 1:
            raise ValidationError("Segment tariff must connect adjacent stops.")
        tariff_by_pair[(from_order, to_order)] = tariff

    segment_snapshot: list[dict[str, Any]] = []
    passenger_base_price = 0
    currency = "DZD"
    for idx in range(1, len(stops)):
        pair = (idx, idx + 1)
        tariff = tariff_by_pair.get(pair)
        if tariff is None:
            raise ValidationError(f"Missing active tariff for segment {idx}->{idx+1}.")
        passenger_base_price += tariff.passenger_price
        currency = tariff.currency or currency
        segment_snapshot.append(
            {
                "from_stop_order": idx,
                "to_stop_order": idx + 1,
                "from_office_id": stops[idx - 1].office_id,
                "to_office_id": stops[idx].office_id,
                "passenger_price": tariff.passenger_price,
                "currency": currency,
            }
        )

    stop_snapshot = [
        {
            "stop_order": stop.stop_order,
            "office_id": stop.office_id,
            "office_name": stop.office.name,
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

    stop_index_by_name = {
        str(stop.get("office_name", "")).strip().lower(): int(stop.get("stop_order", 0))
        for stop in stops
    }
    start_idx = stop_index_by_name.get(boarding_point.strip().lower())
    end_idx = stop_index_by_name.get(alighting_point.strip().lower())
    if not start_idx:
        raise ValidationError({"boarding_point": "Boarding point is not part of this trip route."})
    if not end_idx:
        raise ValidationError({"alighting_point": "Destination is not part of this trip route."})
    if end_idx <= start_idx:
        raise ValidationError({"alighting_point": "Destination must be after boarding stop on this route."})

    price = 0
    segment_map = {
        (int(seg.get("from_stop_order", 0)), int(seg.get("to_stop_order", 0))): int(seg.get("passenger_price", 0))
        for seg in segments
    }
    for stop_order in range(start_idx, end_idx):
        seg_price = segment_map.get((stop_order, stop_order + 1))
        if seg_price is None:
            raise ValidationError("Missing segment tariff in trip route snapshot.")
        price += seg_price
    if price <= 0:
        raise ValidationError("Computed passenger fare is invalid.")
    return price


@transaction.atomic
def create_reverse_template(source_template: RouteTemplate) -> RouteTemplate:
    source_stops = list(source_template.stops.select_related("office").order_by("stop_order"))
    if len(source_stops) < 2:
        raise ValidationError("Cannot reverse template with fewer than two stops.")

    if RouteTemplate.objects.filter(
        start_office=source_template.end_office,
        end_office=source_template.start_office,
        direction="reverse",
        is_deleted=False,
    ).exists():
        raise ValidationError("A reverse template already exists for this endpoint pair.")

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
        is_active=source_template.is_active,
    )

    reversed_stops = list(reversed(source_stops))
    new_stops: list[RouteTemplateStop] = []
    for idx, stop in enumerate(reversed_stops, start=1):
        new_stops.append(
            RouteTemplateStop.objects.create(
                route_template=reverse_template,
                office=stop.office,
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
        if old_tariff is None:
            raise ValidationError("Source template has incomplete segment tariffs; cannot reverse.")

        RouteTemplateSegmentTariff.objects.create(
            route_template=reverse_template,
            from_stop=new_order_to_stop[idx],
            to_stop=new_order_to_stop[idx + 1],
            passenger_price=old_tariff.passenger_price,
            currency=old_tariff.currency,
            is_active=old_tariff.is_active,
        )

    return reverse_template
