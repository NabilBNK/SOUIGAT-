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

export function inferTripStatusFromActivity(
    trip: Pick<Trip, 'status' | 'passenger_count' | 'cargo_count' | 'expense_total'>,
): TripStatus {
    if (trip.status !== 'scheduled') {
        return trip.status
    }

    const hasPassengerActivity = (trip.passenger_count ?? 0) > 0
    const hasCargoActivity = (trip.cargo_count ?? 0) > 0
    const hasExpenseActivity = (trip.expense_total ?? 0) > 0
    if (hasPassengerActivity || hasCargoActivity || hasExpenseActivity) {
        // Backend trip status can lag while ticket/cargo sync has already arrived.
        return 'in_progress'
    }

    return trip.status
}
