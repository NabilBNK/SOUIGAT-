import { useEffect, useState } from 'react'
import { getSyncSummary, getSyncSummaryByEntity, syncQueueEvents } from '../sync/queue'
import type { SyncEntitySummary, SyncStatusSnapshot, SyncSummary } from '../sync/types'

const EMPTY_SUMMARY: SyncSummary = {
    pending: 0,
    inProgress: 0,
    failed: 0,
    conflict: 0,
    lastSyncedAt: null,
}

const EMPTY_BY_ENTITY: SyncEntitySummary = {
    trip: { pending: 0, inProgress: 0, failed: 0, conflict: 0 },
    passenger_ticket: { pending: 0, inProgress: 0, failed: 0, conflict: 0 },
    cargo_ticket: { pending: 0, inProgress: 0, failed: 0, conflict: 0 },
    trip_expense: { pending: 0, inProgress: 0, failed: 0, conflict: 0 },
    settlement: { pending: 0, inProgress: 0, failed: 0, conflict: 0 },
}

export function useSyncStatus() {
    const [snapshot, setSnapshot] = useState<SyncStatusSnapshot>({
        summary: EMPTY_SUMMARY,
        byEntity: EMPTY_BY_ENTITY,
    })

    useEffect(() => {
        let mounted = true

        const refresh = async () => {
            const [summary, byEntity] = await Promise.all([
                getSyncSummary(),
                getSyncSummaryByEntity(),
            ])
            if (mounted) {
                setSnapshot({ summary, byEntity })
            }
        }

        const onChanged = () => {
            void refresh()
        }

        void refresh()
        syncQueueEvents.addEventListener('changed', onChanged)
        const intervalId = window.setInterval(() => {
            void refresh()
        }, 5000)

        return () => {
            mounted = false
            syncQueueEvents.removeEventListener('changed', onChanged)
            window.clearInterval(intervalId)
        }
    }, [])

    return snapshot
}
