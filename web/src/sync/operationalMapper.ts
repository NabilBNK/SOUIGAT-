import type { CargoTicket, PassengerTicket } from '../types/ticket'
import type { Settlement } from '../types/settlement'
import type { Trip } from '../types/trip'
import type { SyncEntityType } from './types'
import { mapTripToMirrorDocument } from './tripMapper'

type LooseObject = Record<string, unknown>

export const COLLECTION_BY_ENTITY: Record<SyncEntityType, string> = {
    trip: 'trip_mirror_v1',
    passenger_ticket: 'passenger_ticket_mirror_v1',
    cargo_ticket: 'cargo_ticket_mirror_v1',
    trip_expense: 'trip_expense_mirror_v1',
    settlement: 'settlement_mirror_v1',
    pricing_config: 'pricing_config_mirror_v1',
    route_template: 'route_template_mirror_v1',
}

function toObject(payload: unknown): LooseObject {
    if (!payload || typeof payload !== 'object') {
        return {}
    }
    return payload as LooseObject
}

function numberValue(payload: LooseObject, key: string): number | null {
    const value = payload[key]
    if (typeof value === 'number') {
        return value
    }
    if (typeof value === 'string') {
        const parsed = Number(value)
        return Number.isFinite(parsed) ? parsed : null
    }
    return null
}

function stringValue(payload: LooseObject, key: string): string | null {
    const value = payload[key]
    return typeof value === 'string' ? value : null
}

function toEpochMillis(value: string | null): number | null {
    if (!value) {
        return null
    }
    const parsed = Date.parse(value)
    return Number.isNaN(parsed) ? null : parsed
}

function mapPassengerTicket(payload: PassengerTicket, opId: string, sourceUpdatedAt: string): LooseObject {
    return {
        entity: 'passenger_ticket',
        id: payload.id,
        trip_id: payload.trip,
        ticket_number: payload.ticket_number,
        passenger_name: payload.passenger_name,
        seat_number: payload.seat_number,
        price: payload.price,
        currency: payload.currency,
        payment_source: payload.payment_source,
        status: payload.status,
        created_by_id: payload.created_by,
        created_by_name: payload.created_by_name,
        version: payload.version,
        source_created_at: payload.created_at,
        source_updated_at: sourceUpdatedAt,
        is_deleted: false,
        deleted_at: null,
        sync_version: 1,
        last_op_id: opId,
    }
}

function mapCargoTicket(payload: CargoTicket, opId: string, sourceUpdatedAt: string): LooseObject {
    return {
        entity: 'cargo_ticket',
        id: payload.id,
        trip_id: payload.trip,
        trip_destination_office_id: payload.trip_destination_office_id,
        ticket_number: payload.ticket_number,
        sender_name: payload.sender_name,
        sender_phone: payload.sender_phone,
        receiver_name: payload.receiver_name,
        receiver_phone: payload.receiver_phone,
        cargo_tier: payload.cargo_tier,
        description: payload.description,
        price: payload.price,
        currency: payload.currency,
        payment_source: payload.payment_source,
        status: payload.status,
        status_override_reason: payload.status_override_reason,
        status_override_by: payload.status_override_by,
        delivered_at: payload.delivered_at,
        delivered_by: payload.delivered_by,
        created_by: payload.created_by,
        created_by_name: payload.created_by_name,
        version: payload.version,
        source_created_at: payload.created_at,
        source_updated_at: sourceUpdatedAt,
        is_deleted: false,
        deleted_at: null,
        sync_version: 1,
        last_op_id: opId,
    }
}

function mapTripExpense(payload: LooseObject, opId: string, sourceUpdatedAt: string): LooseObject {
    const sourceCreatedAt = stringValue(payload, 'created_at') ?? sourceUpdatedAt
    return {
        entity: 'trip_expense',
        id: numberValue(payload, 'id'),
        trip_id: numberValue(payload, 'trip'),
        description: stringValue(payload, 'description') ?? '',
        category: stringValue(payload, 'category') ?? 'other',
        amount: numberValue(payload, 'amount') ?? 0,
        currency: stringValue(payload, 'currency') ?? 'DZD',
        created_by_id: numberValue(payload, 'created_by'),
        created_by_name: stringValue(payload, 'created_by_name') ?? '',
        source_created_at: sourceCreatedAt,
        source_updated_at: sourceUpdatedAt,
        source_updated_ts: toEpochMillis(sourceUpdatedAt),
        is_deleted: false,
        deleted_at: null,
        sync_version: 1,
        last_op_id: opId,
    }
}

function mapSettlement(payload: Settlement, opId: string, sourceUpdatedAt: string): LooseObject {
    return {
        entity: 'settlement',
        id: payload.id,
        trip_id: payload.trip,
        trip_status: payload.trip_status,
        office_id: payload.office,
        office_name: payload.office_name,
        conductor_id: payload.conductor,
        conductor_name: payload.conductor_name,
        settled_by_id: payload.settled_by,
        expected_passenger_cash: payload.expected_passenger_cash,
        expected_cargo_cash: payload.expected_cargo_cash,
        expected_total_cash: payload.expected_total_cash,
        agency_presale_total: payload.agency_presale_total,
        outstanding_cargo_delivery: payload.outstanding_cargo_delivery,
        expenses_to_reimburse: payload.expenses_to_reimburse,
        net_cash_expected: payload.net_cash_expected,
        actual_cash_received: payload.actual_cash_received,
        actual_expenses_reimbursed: payload.actual_expenses_reimbursed,
        discrepancy_amount: payload.discrepancy_amount,
        status: payload.status,
        notes: payload.notes,
        dispute_reason: payload.dispute_reason,
        source_created_at: payload.created_at,
        source_updated_at: sourceUpdatedAt,
        settled_at: payload.settled_at,
        disputed_at: payload.disputed_at,
        resolved_at: payload.resolved_at,
        is_deleted: false,
        deleted_at: null,
        sync_version: 1,
        last_op_id: opId,
    }
}

export function mapEntityUpsertDocument(
    entityType: SyncEntityType,
    payload: unknown,
    opId: string,
    sourceUpdatedAt: string,
): LooseObject {
    if (entityType === 'trip') {
        return mapTripToMirrorDocument(payload as Trip, opId)
    }

    if (entityType === 'passenger_ticket') {
        return mapPassengerTicket(payload as PassengerTicket, opId, sourceUpdatedAt)
    }

    if (entityType === 'cargo_ticket') {
        return mapCargoTicket(payload as CargoTicket, opId, sourceUpdatedAt)
    }

    if (entityType === 'trip_expense') {
        return mapTripExpense(toObject(payload), opId, sourceUpdatedAt)
    }

    if (entityType === 'pricing_config' || entityType === 'route_template') {
        // Backend mirrors these directly — pass through the raw payload as-is.
        return { ...toObject(payload), last_op_id: opId, source_updated_at: sourceUpdatedAt }
    }

    return mapSettlement(payload as Settlement, opId, sourceUpdatedAt)
}

export function mapEntityDeleteDocument(
    entityType: SyncEntityType,
    entityId: string,
    opId: string,
    sourceUpdatedAt: string,
): LooseObject {
    return {
        entity: entityType,
        id: Number(entityId),
        source_updated_at: sourceUpdatedAt,
        is_deleted: true,
        deleted_at: sourceUpdatedAt,
        sync_version: 1,
        last_op_id: opId,
    }
}
