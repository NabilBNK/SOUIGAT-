import { openDB, type DBSchema, type IDBPDatabase } from 'idb'
import type {
    SyncEntityType,
    SyncEntitySummary,
    SyncRecord,
    SyncRecordDraft,
    SyncRecordStatus,
    SyncSummary,
} from './types'

const DB_NAME = 'souigat-sync-db'
const DB_VERSION = 1
const STORE_NAME = 'sync_queue'
const DEFAULT_MAX_RETRIES = 8
const RETAIN_SYNCED_FOR_MS = 7 * 24 * 60 * 60 * 1000

function createEmptyEntitySummary(): SyncEntitySummary {
    return {
        trip: { pending: 0, inProgress: 0, failed: 0, conflict: 0 },
        passenger_ticket: { pending: 0, inProgress: 0, failed: 0, conflict: 0 },
        cargo_ticket: { pending: 0, inProgress: 0, failed: 0, conflict: 0 },
        trip_expense: { pending: 0, inProgress: 0, failed: 0, conflict: 0 },
        settlement: { pending: 0, inProgress: 0, failed: 0, conflict: 0 },
    }
}

interface SyncDbSchema extends DBSchema {
    [STORE_NAME]: {
        key: number
        value: SyncRecord
        indexes: {
            by_status: SyncRecordStatus
            by_dedupe: string
            by_next_retry: number
        }
    }
}

let dbPromise: Promise<IDBPDatabase<SyncDbSchema>> | null = null

export const syncQueueEvents = new EventTarget()

function emitQueueChanged() {
    syncQueueEvents.dispatchEvent(new Event('changed'))
}

function getDb() {
    if (!dbPromise) {
        dbPromise = openDB<SyncDbSchema>(DB_NAME, DB_VERSION, {
            upgrade(db) {
                const store = db.createObjectStore(STORE_NAME, {
                    keyPath: 'id',
                    autoIncrement: true,
                })
                store.createIndex('by_status', 'status', { unique: false })
                store.createIndex('by_dedupe', 'dedupeKey', { unique: true })
                store.createIndex('by_next_retry', 'nextRetryAt', { unique: false })
            },
        })
    }
    return dbPromise
}

function now() {
    return Date.now()
}

export async function enqueueSyncRecord(draft: SyncRecordDraft): Promise<void> {
    const db = await getDb()
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    const dedupeIndex = store.index('by_dedupe')
    const existing = await dedupeIndex.get(draft.dedupeKey)
    const timestamp = now()

    if (existing) {
        const nextRecord: SyncRecord = {
            ...existing,
            ...draft,
            status: 'pending',
            retryCount: 0,
            maxRetries: draft.maxRetries ?? existing.maxRetries,
            nextRetryAt: timestamp,
            lastError: null,
            updatedAt: timestamp,
            syncedAt: null,
        }
        await store.put(nextRecord)
    } else {
        const record: SyncRecord = {
            ...draft,
            status: 'pending',
            retryCount: 0,
            maxRetries: draft.maxRetries ?? DEFAULT_MAX_RETRIES,
            nextRetryAt: timestamp,
            lastError: null,
            createdAt: timestamp,
            updatedAt: timestamp,
            syncedAt: null,
        }
        await store.add(record)
    }

    await pruneOldSyncedRecordsInternal(store, timestamp)
    await tx.done
    emitQueueChanged()
}

export async function getReadySyncRecords(limit = 25): Promise<SyncRecord[]> {
    const db = await getDb()
    const all = await db.getAll(STORE_NAME)
    const timestamp = now()
    return all
        .filter((record) => {
            if (record.status !== 'pending' && record.status !== 'failed') {
                return false
            }
            return record.nextRetryAt <= timestamp
        })
        .sort((a, b) => {
            if (a.nextRetryAt === b.nextRetryAt) {
                return a.createdAt - b.createdAt
            }
            return a.nextRetryAt - b.nextRetryAt
        })
        .slice(0, limit)
}

async function updateRecord(
    id: number,
    updater: (record: SyncRecord) => SyncRecord,
): Promise<void> {
    const db = await getDb()
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    const current = await store.get(id)

    if (!current) {
        await tx.done
        return
    }

    const nextRecord = updater(current)
    await store.put(nextRecord)
    await tx.done
    emitQueueChanged()
}

export async function markSyncRecordInProgress(id: number): Promise<void> {
    await updateRecord(id, (record) => ({
        ...record,
        status: 'in_progress',
        updatedAt: now(),
    }))
}

export async function markSyncRecordSynced(id: number): Promise<void> {
    const timestamp = now()
    await updateRecord(id, (record) => ({
        ...record,
        status: 'synced',
        lastError: null,
        syncedAt: timestamp,
        updatedAt: timestamp,
    }))
}

export async function markSyncRecordConflict(id: number, message: string): Promise<void> {
    await updateRecord(id, (record) => ({
        ...record,
        status: 'conflict',
        lastError: message,
        updatedAt: now(),
    }))
}

export async function markSyncRecordFailed(
    id: number,
    message: string,
    nextRetryAt: number,
    terminal: boolean,
): Promise<void> {
    await updateRecord(id, (record) => {
        const retryCount = record.retryCount + 1
        return {
            ...record,
            retryCount,
            status: 'failed',
            lastError: message,
            nextRetryAt: terminal ? Number.MAX_SAFE_INTEGER : nextRetryAt,
            updatedAt: now(),
        }
    })
}

export async function hasPendingSyncWork(): Promise<boolean> {
    const db = await getDb()
    const all = await db.getAll(STORE_NAME)
    const timestamp = now()
    return all.some((record) => {
        if (record.status === 'pending' || record.status === 'in_progress') {
            return true
        }
        if (record.status === 'failed' && record.nextRetryAt <= timestamp) {
            return true
        }
        return false
    })
}

export async function getSyncSummary(): Promise<SyncSummary> {
    const db = await getDb()
    const all = await db.getAll(STORE_NAME)

    let pending = 0
    let inProgress = 0
    let failed = 0
    let conflict = 0
    let lastSyncedAt: number | null = null

    all.forEach((record) => {
        switch (record.status) {
            case 'pending':
                pending += 1
                break
            case 'in_progress':
                inProgress += 1
                break
            case 'failed':
                failed += 1
                break
            case 'conflict':
                conflict += 1
                break
            case 'synced':
                if (record.syncedAt && (!lastSyncedAt || record.syncedAt > lastSyncedAt)) {
                    lastSyncedAt = record.syncedAt
                }
                break
        }
    })

    return {
        pending,
        inProgress,
        failed,
        conflict,
        lastSyncedAt,
    }
}

export async function getSyncSummaryByEntity(): Promise<SyncEntitySummary> {
    const db = await getDb()
    const all = await db.getAll(STORE_NAME)
    const summary = createEmptyEntitySummary()

    all.forEach((record) => {
        const entity = summary[record.entityType]
        if (!entity) {
            return
        }

        switch (record.status) {
            case 'pending':
                entity.pending += 1
                break
            case 'in_progress':
                entity.inProgress += 1
                break
            case 'failed':
                entity.failed += 1
                break
            case 'conflict':
                entity.conflict += 1
                break
            default:
                break
        }
    })

    return summary
}

export async function getLatestSyncIssueForEntity(
    entityType: SyncEntityType,
    entityId: string,
): Promise<SyncRecord | null> {
    const db = await getDb()
    const all = await db.getAll(STORE_NAME)

    const candidate = all
        .filter((record) => (
            record.entityType === entityType
            && record.entityId === entityId
            && (record.status === 'failed' || record.status === 'conflict')
        ))
        .sort((a, b) => b.updatedAt - a.updatedAt)[0]

    return candidate ?? null
}

export async function getLatestSyncRecordForEntity(
    entityType: SyncEntityType,
    entityId: string,
): Promise<SyncRecord | null> {
    const db = await getDb()
    const all = await db.getAll(STORE_NAME)

    const candidate = all
        .filter((record) => (
            record.entityType === entityType
            && record.entityId === entityId
        ))
        .sort((a, b) => b.updatedAt - a.updatedAt)[0]

    return candidate ?? null
}

async function pruneOldSyncedRecordsInternal(
    store: {
        getAll: () => Promise<SyncRecord[]>
        delete: (key: number) => Promise<void>
    },
    timestamp: number,
) {
    const allRecords = await store.getAll()
    const threshold = timestamp - RETAIN_SYNCED_FOR_MS
    const deletions = allRecords
        .filter((record) => record.status === 'synced' && (record.syncedAt ?? 0) < threshold)
        .map((record) => record.id)
        .filter((id): id is number => typeof id === 'number')

    await Promise.all(deletions.map((id) => store.delete(id)))
}
