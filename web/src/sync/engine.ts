import {
    doc,
    runTransaction,
    serverTimestamp,
    type Firestore,
} from 'firebase/firestore'
import { ensureFirebaseSession } from '../firebase/auth'
import { getFirebaseFirestore } from '../firebase/firestore'
import type { Trip } from '../types/trip'
import { COLLECTION_BY_ENTITY, mapEntityDeleteDocument, mapEntityUpsertDocument } from './operationalMapper'
import {
    getReadySyncRecords,
    hasPendingSyncWork,
    markSyncRecordConflict,
    markSyncRecordFailed,
    markSyncRecordInProgress,
    markSyncRecordSynced,
} from './queue'
import type { SyncRecord } from './types'

const SYNC_BATCH_SIZE = 25
const RETRY_BASE_MS = 2000
const RETRY_MAX_MS = 120000

let engineStarted = false
let syncDrainInProgress = false
let nextDrainTimer: number | null = null

class StaleRecordConflictError extends Error {
    constructor(message: string) {
        super(message)
        this.name = 'StaleRecordConflictError'
    }
}

function toMillis(value: unknown): number {
    if (typeof value === 'number') {
        return value
    }

    if (typeof value === 'string') {
        const parsed = Date.parse(value)
        return Number.isNaN(parsed) ? 0 : parsed
    }

    if (value && typeof value === 'object' && 'toMillis' in value) {
        const candidate = value as { toMillis?: () => number }
        if (typeof candidate.toMillis === 'function') {
            return candidate.toMillis()
        }
    }

    return 0
}

function stringifyError(error: unknown): string {
    if (error instanceof Error) {
        return error.message
    }
    return String(error)
}

function getFirebaseErrorCode(error: unknown): string | null {
    if (!error || typeof error !== 'object') {
        return null
    }
    const code = (error as { code?: unknown }).code
    return typeof code === 'string' ? code : null
}

function isRetryableError(error: unknown): boolean {
    const code = getFirebaseErrorCode(error)
    if (!code) {
        return true
    }
    return [
        'aborted',
        'cancelled',
        'deadline-exceeded',
        'internal',
        'resource-exhausted',
        'unavailable',
        'unknown',
        'unauthenticated',
    ].includes(code)
}

function computeNextRetryAt(retryCount: number): number {
    const expo = RETRY_BASE_MS * (2 ** Math.max(0, retryCount - 1))
    const jitter = Math.floor(Math.random() * 700)
    return Date.now() + Math.min(RETRY_MAX_MS, expo) + jitter
}

function isTripPayload(payload: unknown): payload is Trip {
    if (!payload || typeof payload !== 'object') {
        return false
    }

    const candidate = payload as Partial<Trip>
    return typeof candidate.id === 'number'
        && typeof candidate.updated_at === 'string'
        && typeof candidate.created_at === 'string'
}

async function syncTripUpsert(record: SyncRecord, db: Firestore): Promise<void> {
    if (record.entityType === 'trip' && !isTripPayload(record.payload)) {
        throw new Error('Trip payload missing for upsert operation.')
    }

    const collection = COLLECTION_BY_ENTITY[record.entityType]
    const docRef = doc(db, collection, String(record.entityId))
    const incomingUpdatedAtMs = toMillis(record.sourceUpdatedAt)

    await runTransaction(db, async (transaction) => {
        const snapshot = await transaction.get(docRef)
        const existing = snapshot.exists() ? snapshot.data() : null

        if (existing) {
            const lastOpId = existing.last_op_id
            if (typeof lastOpId === 'string' && lastOpId === record.opId) {
                return
            }

            const existingUpdatedAtMs = toMillis(existing.source_updated_at)
            if (existingUpdatedAtMs > incomingUpdatedAtMs) {
                throw new StaleRecordConflictError(
                    `Incoming trip ${record.entityId} is older than Firestore document.`,
                )
            }
        }

        transaction.set(docRef, {
            ...mapEntityUpsertDocument(
                record.entityType,
                record.payload,
                record.opId,
                record.sourceUpdatedAt,
            ),
            mirrored_at: serverTimestamp(),
        }, { merge: true })
    })
}

async function syncTripDelete(record: SyncRecord, db: Firestore): Promise<void> {
    const collection = COLLECTION_BY_ENTITY[record.entityType]
    const docRef = doc(db, collection, String(record.entityId))
    const incomingUpdatedAtMs = toMillis(record.sourceUpdatedAt)

    await runTransaction(db, async (transaction) => {
        const snapshot = await transaction.get(docRef)
        const existing = snapshot.exists() ? snapshot.data() : null

        if (existing) {
            const lastOpId = existing.last_op_id
            if (typeof lastOpId === 'string' && lastOpId === record.opId) {
                return
            }

            const existingUpdatedAtMs = toMillis(existing.source_updated_at)
            if (existingUpdatedAtMs > incomingUpdatedAtMs) {
                throw new StaleRecordConflictError(
                    `Incoming trip delete ${record.entityId} is older than Firestore document.`,
                )
            }
        }

        transaction.set(docRef, {
            ...mapEntityDeleteDocument(
                record.entityType,
                record.entityId,
                record.opId,
                record.sourceUpdatedAt,
            ),
            deleted_at: serverTimestamp(),
            mirrored_at: serverTimestamp(),
        }, { merge: true })
    })
}

async function pushRecordToFirebase(record: SyncRecord, db: Firestore): Promise<void> {
    if (!COLLECTION_BY_ENTITY[record.entityType]) {
        throw new Error(`Unsupported sync entity: ${record.entityType}`)
    }

    if (record.operation === 'upsert') {
        await syncTripUpsert(record, db)
        return
    }

    if (record.operation === 'delete') {
        await syncTripDelete(record, db)
        return
    }

    throw new Error(`Unsupported sync operation: ${record.operation}`)
}

async function processRecord(record: SyncRecord, db: Firestore): Promise<void> {
    if (typeof record.id !== 'number') {
        return
    }

    await markSyncRecordInProgress(record.id)

    try {
        await pushRecordToFirebase(record, db)
        await markSyncRecordSynced(record.id)
    } catch (error) {
        if (error instanceof StaleRecordConflictError) {
            await markSyncRecordConflict(record.id, error.message)
            return
        }

        const code = getFirebaseErrorCode(error)
        if (code === 'unauthenticated') {
            await ensureFirebaseSession(true)
        }

        const retryable = isRetryableError(error)
        const nextRetryAt = computeNextRetryAt(record.retryCount + 1)
        const terminal = !retryable || (record.retryCount + 1) > record.maxRetries

        await markSyncRecordFailed(
            record.id,
            stringifyError(error),
            nextRetryAt,
            terminal,
        )
    }
}

async function drainSyncQueue(): Promise<void> {
    if (!engineStarted || syncDrainInProgress) {
        return
    }

    syncDrainInProgress = true
    try {
        if (!navigator.onLine) {
            scheduleDrain(3000)
            return
        }

        const hasWork = await hasPendingSyncWork()
        if (!hasWork) {
            return
        }

        const db = getFirebaseFirestore()
        if (!db) {
            return
        }

        const ready = await ensureFirebaseSession()
        if (!ready) {
            scheduleDrain(20000)
            return
        }

        const records = await getReadySyncRecords(SYNC_BATCH_SIZE)
        for (const record of records) {
            await processRecord(record, db)
        }

        if (await hasPendingSyncWork()) {
            scheduleDrain(500)
        }
    } finally {
        syncDrainInProgress = false
    }
}

function scheduleDrain(delayMs: number) {
    if (!engineStarted) {
        return
    }

    if (nextDrainTimer !== null) {
        window.clearTimeout(nextDrainTimer)
    }

    nextDrainTimer = window.setTimeout(() => {
        nextDrainTimer = null
        void drainSyncQueue()
    }, delayMs)
}

function handleOnline() {
    scheduleDrain(0)
}

export function startSyncEngine() {
    if (engineStarted) {
        return
    }
    engineStarted = true
    window.addEventListener('online', handleOnline)
    scheduleDrain(500)
}

export function stopSyncEngine() {
    engineStarted = false
    window.removeEventListener('online', handleOnline)
    if (nextDrainTimer !== null) {
        window.clearTimeout(nextDrainTimer)
        nextDrainTimer = null
    }
}

export function requestSyncDrain(delayMs = 0) {
    scheduleDrain(delayMs)
}
