export type SyncEntityType =
    | 'trip'
    | 'passenger_ticket'
    | 'cargo_ticket'
    | 'trip_expense'
    | 'settlement'
export type SyncOperation = 'upsert' | 'delete'
export type SyncRecordStatus = 'pending' | 'in_progress' | 'synced' | 'failed' | 'conflict'

export interface SyncEntityCounters {
    pending: number
    inProgress: number
    failed: number
    conflict: number
}

export type SyncEntitySummary = Record<SyncEntityType, SyncEntityCounters>

export interface SyncRecord {
    id?: number
    entityType: SyncEntityType
    entityId: string
    operation: SyncOperation
    payload: unknown
    sourceUpdatedAt: string
    opId: string
    dedupeKey: string
    status: SyncRecordStatus
    retryCount: number
    maxRetries: number
    nextRetryAt: number
    lastError: string | null
    createdAt: number
    updatedAt: number
    syncedAt: number | null
}

export interface SyncRecordDraft {
    entityType: SyncEntityType
    entityId: string
    operation: SyncOperation
    payload: unknown
    sourceUpdatedAt: string
    opId: string
    dedupeKey: string
    maxRetries?: number
}

export interface SyncSummary {
    pending: number
    inProgress: number
    failed: number
    conflict: number
    lastSyncedAt: number | null
}

export interface SyncStatusSnapshot {
    summary: SyncSummary
    byEntity: SyncEntitySummary
}
