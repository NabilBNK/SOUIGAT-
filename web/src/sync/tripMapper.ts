import type { Trip } from '../types/trip'

function toEpochMillis(value: string): number | null {
    const parsed = Date.parse(value)
    return Number.isNaN(parsed) ? null : parsed
}

export function mapTripToMirrorDocument(trip: Trip, opId: string): Record<string, unknown> {
    return {
        entity: 'trip',
        id: trip.id,
        status: trip.status,
        origin_office_id: trip.origin_office,
        origin_office_name: trip.origin_office_name,
        destination_office_id: trip.destination_office,
        destination_office_name: trip.destination_office_name,
        office_scope_ids: [trip.origin_office, trip.destination_office],
        conductor_id: trip.conductor,
        conductor_name: trip.conductor_name,
        bus_id: trip.bus,
        bus_plate: trip.bus_plate,
        departure_datetime: trip.departure_datetime,
        departure_ts: toEpochMillis(trip.departure_datetime),
        arrival_datetime: trip.arrival_datetime,
        passenger_base_price: trip.passenger_base_price,
        cargo_small_price: trip.cargo_small_price,
        cargo_medium_price: trip.cargo_medium_price,
        cargo_large_price: trip.cargo_large_price,
        currency: trip.currency,
        passenger_count: trip.passenger_count ?? 0,
        cargo_count: trip.cargo_count ?? 0,
        expense_total: trip.expense_total ?? 0,
        source_created_at: trip.created_at,
        source_updated_at: trip.updated_at,
        is_deleted: false,
        deleted_at: null,
        sync_version: 1,
        last_op_id: opId,
    }
}

export function mapTripDeleteDocument(
    tripId: number,
    opId: string,
    sourceUpdatedAt: string,
): Record<string, unknown> {
    return {
        entity: 'trip',
        id: tripId,
        source_updated_at: sourceUpdatedAt,
        is_deleted: true,
        sync_version: 1,
        last_op_id: opId,
    }
}
