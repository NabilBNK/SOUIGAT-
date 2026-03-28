package com.souigat.mobile.data.local.dao

import androidx.room.*
import com.souigat.mobile.data.local.entity.SyncQueueEntity
import com.souigat.mobile.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

data class SyncingTypeCount(
    val itemType: String,
    val count: Int,
)

@Dao
interface SyncQueueDao {

    /** Observe queued items for reactive sync status UI. */
    @Query("SELECT * FROM sync_queue WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    fun observePending(): Flow<List<SyncQueueEntity>>

    /**
     * Reactive count of items still waiting for a completed upload.
     * Includes in-flight rows to avoid false "synced" green states while worker is running.
     */
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('PENDING', 'SYNCING', 'FAILED')")
    fun observePendingCount(): Flow<Int>

    /** Reactive count of QUARANTINED items — drives QuarantineWarningBanner. */
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'QUARANTINED'")
    fun observeQuarantinedCount(): Flow<Int>

    /** Reactive count of SYNCED items — used by Profile sync stats. */
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'SYNCED'")
    fun observeSyncedCount(): Flow<Int>

    @Query("SELECT MAX(syncedAt) FROM sync_queue WHERE status = 'SYNCED'")
    fun observeLastSyncedAt(): Flow<Long?>

    /** Fetch a due batch for the SyncWorker to process. */
    @Query(
        "SELECT * FROM sync_queue " +
            "WHERE status IN ('PENDING', 'FAILED') " +
            "AND nextAttemptAt <= :nowMillis " +
            "ORDER BY createdAt ASC LIMIT :limit"
    )
    suspend fun getPendingBatch(nowMillis: Long, limit: Int = 50): List<SyncQueueEntity>

    /** Count queued items waiting for upload retry/sync. */
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('PENDING', 'FAILED')")
    suspend fun getPendingCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueAll(items: List<SyncQueueEntity>): List<Long>

    /**
     * Mark accepted items as SYNCED by their local Room ID.
     *
     * ⚡ AUDIT FIX (v7): Uses LOCAL id (Room auto-increment), NOT the backend server ID.
     * The backend echoes local_id in the response so we can find the right row.
     * markSyncedByBackendId() was the bug — backend IDs and local IDs are different numbers.
     */
    @Query(
        "UPDATE sync_queue " +
            "SET status = 'SYNCED', syncedAt = :syncedAt, deadLetterReason = NULL, lastErrorCode = NULL, lastErrorMessage = NULL " +
            "WHERE id IN (:localIds)"
    )
    suspend fun markSyncedByLocalId(localIds: List<Long>, syncedAt: Long = System.currentTimeMillis())

    @Query(
        "UPDATE sync_queue " +
            "SET status = 'QUARANTINED', deadLetterReason = :reason, lastErrorCode = :errorCode, lastErrorMessage = :errorMessage " +
            "WHERE id IN (:localIds)"
    )
    suspend fun markQuarantinedByLocalId(
        localIds: List<Long>,
        reason: String,
        errorCode: String? = null,
        errorMessage: String? = null,
    )

    @Query("UPDATE sync_queue SET status = 'SYNCING', lastAttemptAt = :attemptedAt WHERE id IN (:localIds)")
    suspend fun markSyncing(localIds: List<Long>, attemptedAt: Long = System.currentTimeMillis())

    @Query(
        "UPDATE sync_queue " +
            "SET status = 'FAILED', retryCount = retryCount + 1, nextAttemptAt = :nextAttemptAt, " +
            "lastErrorCode = :errorCode, lastErrorMessage = :errorMessage " +
            "WHERE id = :localId"
    )
    suspend fun markFailed(
        localId: Long,
        nextAttemptAt: Long,
        errorCode: String? = null,
        errorMessage: String? = null,
    )

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'SYNCING' AND syncedAt IS NULL")
    suspend fun countStuckSyncing(): Int

    @Query("SELECT MIN(createdAt) FROM sync_queue WHERE status = 'SYNCING' AND syncedAt IS NULL")
    suspend fun oldestStuckSyncingCreatedAt(): Long?

    @Query(
        "SELECT itemType, COUNT(*) AS count FROM sync_queue " +
            "WHERE status = 'SYNCING' AND syncedAt IS NULL GROUP BY itemType"
    )
    suspend fun stuckSyncingTypeCounts(): List<SyncingTypeCount>

    /** Reset SYNCING → PENDING after a crash/interrupt to prevent stuck items.
     *  Only resets items that were never processed (syncedAt IS NULL). */
    @Query("UPDATE sync_queue SET status = 'PENDING' WHERE status = 'SYNCING' AND syncedAt IS NULL")
    suspend fun resetStuckSyncing(): Int

    @Query("UPDATE sync_queue SET nextAttemptAt = :nowMillis WHERE status = 'FAILED'")
    suspend fun expediteFailed(nowMillis: Long = System.currentTimeMillis()): Int

    @Query(
        "UPDATE sync_queue " +
            "SET status = 'PENDING', retryCount = 0, deadLetterReason = NULL, lastErrorCode = NULL, lastErrorMessage = NULL, " +
            "nextAttemptAt = :nowMillis " +
            "WHERE status = 'QUARANTINED' AND deadLetterReason IN ('permission_denied', 'backend_rejected')"
    )
    suspend fun retryRecoverableQuarantined(nowMillis: Long = System.currentTimeMillis()): Int

    @Query(
        "UPDATE sync_queue " +
            "SET status = 'PENDING', retryCount = 0, deadLetterReason = NULL, lastErrorCode = NULL, lastErrorMessage = NULL, " +
            "nextAttemptAt = :nowMillis " +
            "WHERE status = 'QUARANTINED' " +
            "AND itemType IN ('passenger_ticket', 'cargo_ticket', 'expense', 'trip_status')"
    )
    suspend fun retryAllOperationalQuarantined(nowMillis: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM sync_queue WHERE status = 'SYNCED' AND syncedAt < :beforeMillis")
    suspend fun pruneOldSynced(beforeMillis: Long)
}
