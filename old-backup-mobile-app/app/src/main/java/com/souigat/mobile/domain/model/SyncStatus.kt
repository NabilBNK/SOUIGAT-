package com.souigat.mobile.domain.model

/**
 * Sync status for items in the offline queue.
 * Stored as String in Room via SyncStatusConverter — NOT as ordinal Int.
 *
 * ⚡ AUDIT FIX (v7): Using TypeConverter for this enum is REQUIRED.
 * If stored as Int (default), all sync queries return 0 results when
 * the enum order changes. String storage is stable across reorders.
 */
enum class SyncStatus {
    PENDING,       // Created locally, not yet sent to server
    SYNCING,       // In-flight — currently being sent
    SYNCED,        // Server accepted and returned success
    QUARANTINED,   // Server quarantined (needs admin review)
    FAILED         // Non-recoverable error — will not retry
}
