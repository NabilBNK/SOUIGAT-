import type { CargoTicket, PassengerTicket } from '../types/ticket'
import type { Settlement } from '../types/settlement'
import { requestSyncDrain } from './engine'
import { enqueueSyncRecord } from './queue'
import type { SyncEntityType } from './types'

interface MirrorRecordInput {
    entityType: SyncEntityType
    entityId: number
    payload: unknown
    sourceUpdatedAt: string
}

interface MirrorDeleteInput {
    entityType: SyncEntityType
    entityId: number
    sourceUpdatedAt: string
}

function toIso(value: unknown): string | null {
    if (typeof value !== 'string') {
        return null
    }
    const parsed = Date.parse(value)
    if (Number.isNaN(parsed)) {
        return null
    }
    return new Date(parsed).toISOString()
}

function resolveSourceUpdatedAt(payload: unknown): string {
    if (!payload || typeof payload !== 'object') {
        return new Date().toISOString()
    }

    const candidate = payload as Record<string, unknown>
    return toIso(candidate.updated_at)
        ?? toIso(candidate.created_at)
        ?? new Date().toISOString()
}

async function queueEntityUpsert(input: MirrorRecordInput): Promise<void> {
    const opId = `${input.entityType}:${input.entityId}:upsert:${input.sourceUpdatedAt}`

    await enqueueSyncRecord({
        entityType: input.entityType,
        entityId: String(input.entityId),
        operation: 'upsert',
        payload: input.payload,
        sourceUpdatedAt: input.sourceUpdatedAt,
        opId,
        dedupeKey: opId,
    })

    requestSyncDrain(0)
}

async function queueEntityDelete(input: MirrorDeleteInput): Promise<void> {
    const opId = `${input.entityType}:${input.entityId}:delete:${input.sourceUpdatedAt}`

    await enqueueSyncRecord({
        entityType: input.entityType,
        entityId: String(input.entityId),
        operation: 'delete',
        payload: null,
        sourceUpdatedAt: input.sourceUpdatedAt,
        opId,
        dedupeKey: `${input.entityType}:${input.entityId}:delete`,
    })

    requestSyncDrain(0)
}

export async function queuePassengerTicketUpsert(ticket: PassengerTicket): Promise<void> {
    await queueEntityUpsert({
        entityType: 'passenger_ticket',
        entityId: ticket.id,
        payload: ticket,
        sourceUpdatedAt: resolveSourceUpdatedAt(ticket),
    })
}

export async function queuePassengerTicketDelete(ticketId: number): Promise<void> {
    await queueEntityDelete({
        entityType: 'passenger_ticket',
        entityId: ticketId,
        sourceUpdatedAt: new Date().toISOString(),
    })
}

export async function queueCargoTicketUpsert(ticket: CargoTicket): Promise<void> {
    await queueEntityUpsert({
        entityType: 'cargo_ticket',
        entityId: ticket.id,
        payload: ticket,
        sourceUpdatedAt: resolveSourceUpdatedAt(ticket),
    })
}

export async function queueCargoTicketDelete(ticketId: number): Promise<void> {
    await queueEntityDelete({
        entityType: 'cargo_ticket',
        entityId: ticketId,
        sourceUpdatedAt: new Date().toISOString(),
    })
}

export async function queueTripExpenseUpsert(expense: unknown): Promise<void> {
    if (!expense || typeof expense !== 'object') {
        return
    }

    const payload = expense as Record<string, unknown>
    const id = typeof payload.id === 'number' ? payload.id : Number(payload.id)
    if (!Number.isFinite(id)) {
        return
    }

    await queueEntityUpsert({
        entityType: 'trip_expense',
        entityId: id,
        payload,
        sourceUpdatedAt: resolveSourceUpdatedAt(payload),
    })
}

export async function queueTripExpenseDelete(expenseId: number): Promise<void> {
    await queueEntityDelete({
        entityType: 'trip_expense',
        entityId: expenseId,
        sourceUpdatedAt: new Date().toISOString(),
    })
}

export async function queueSettlementUpsert(settlement: Settlement): Promise<void> {
    await queueEntityUpsert({
        entityType: 'settlement',
        entityId: settlement.id,
        payload: settlement,
        sourceUpdatedAt: resolveSourceUpdatedAt(settlement),
    })
}
