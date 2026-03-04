package com.souigat.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.souigat.mobile.domain.model.SyncStatus

/**
 * Offline sync queue entry.
 *
 * status is stored as its String name via SyncStatusConverter — NOT as Int ordinal.
 * This is critical: if stored as Int, any reordering of SyncStatus values silently
 * corrupts all existing rows. String storage is safe across enum refactoring.
 *
 * localId: the Room auto-increment ID. Echoed to backend in POST /api/sync/batch/.
 * The backend echoes it back in the response so we can call markSyncedByLocalId().
 *
 * idempotencyKey: content-hash of the payload (see §8.6). Max 100 chars (VARCHAR(100) on server).
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index("status"),
        Index("idempotencyKey", unique = true),
        Index("tripId")
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val itemType: String,            // passenger_ticket | cargo_ticket | expense | trip_status
    val payload: String,             // JSON string
    val idempotencyKey: String,      // SHA-256 content hash, max 100 chars
    val status: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null
)
