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
 * localId: the Room auto-increment ID used by the local sync worker lifecycle.
 *
 * idempotencyKey: UUID v4 generated at ticket creation time. Max 100 chars (VARCHAR(100) on server).
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index("status"),
        Index(value = ["status", "nextAttemptAt"]),
        Index("idempotencyKey", unique = true),
        Index("tripId"),
        Index(value = ["tripId", "createdAt"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val itemType: String,            // passenger_ticket | cargo_ticket | cargo_status | expense | trip_status
    val payload: String,             // JSON string
    val idempotencyKey: String,      // UUID v4 generated at ticket creation time
    val status: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
    val nextAttemptAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    val deadLetterReason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null
)
