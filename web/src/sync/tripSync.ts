import type { Trip } from '../types/trip'
import { requestSyncDrain } from './engine'
import { enqueueSyncRecord } from './queue'

function buildTripUpsertOpId(trip: Trip): string {
    return `trip:${trip.id}:upsert:${trip.updated_at}`
}

function buildTripDeleteOpId(tripId: number, sourceUpdatedAt: string): string {
    return `trip:${tripId}:delete:${sourceUpdatedAt}`
}

export async function queueTripUpsert(trip: Trip): Promise<void> {
    const opId = buildTripUpsertOpId(trip)

    await enqueueSyncRecord({
        entityType: 'trip',
        entityId: String(trip.id),
        operation: 'upsert',
        payload: trip,
        sourceUpdatedAt: trip.updated_at,
        opId,
        dedupeKey: opId,
    })

    requestSyncDrain(0)
}

export async function queueTripDelete(
    tripId: number,
    sourceUpdatedAt = new Date().toISOString(),
): Promise<void> {
    const opId = buildTripDeleteOpId(tripId, sourceUpdatedAt)

    await enqueueSyncRecord({
        entityType: 'trip',
        entityId: String(tripId),
        operation: 'delete',
        payload: null,
        sourceUpdatedAt,
        opId,
        dedupeKey: `trip:${tripId}:delete`,
    })

    requestSyncDrain(0)
}
