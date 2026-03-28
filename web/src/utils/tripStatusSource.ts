import type { Trip, TripStatus } from '../types/trip'

function parseEpochMs(value: string | null | undefined): number | null {
    if (!value) return null
    const timestamp = Date.parse(value)
    return Number.isFinite(timestamp) ? timestamp : null
}

export function shouldPreferMirrorStatus(
    trip: Pick<Trip, 'status' | 'updated_at'>,
    mirror: { status: TripStatus | null; sourceUpdatedAt: string | null } | undefined,
): boolean {
    if (!mirror?.status) {
        return false
    }

    if (trip.status === 'cancelled') {
        return mirror.status === 'cancelled'
    }

    if (mirror.status === 'in_progress' || mirror.status === 'completed') {
        return true
    }

    const backendUpdatedAtMs = parseEpochMs(trip.updated_at)
    const mirrorUpdatedAtMs = parseEpochMs(mirror.sourceUpdatedAt)
    if (backendUpdatedAtMs === null || mirrorUpdatedAtMs === null) {
        return false
    }

    return mirrorUpdatedAtMs >= backendUpdatedAtMs - 2000
}
