package com.souigat.mobile.data.local.dao

import androidx.room.*
import com.souigat.mobile.data.local.entity.SyncQueueEntity
import com.souigat.mobile.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    /** Observe pending items for reactive sync status UI. */
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    fun observePending(): Flow<List<SyncQueueEntity>>

    /** Reactive count of PENDING items — drives the sync badge on Dashboard. */
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    /** Reactive count of QUARANTINED items — drives QuarantineWarningBanner. */
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'QUARANTINED'")
    fun observeQuarantinedCount(): Flow<Int>

    /** Reactive count of SYNCED items — used by Profile sync stats. */
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'SYNCED'")
    fun observeSyncedCount(): Flow<Int>

    /** Fetch a batch of pending items for the SyncWorker to process. */
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPendingBatch(limit: Int = 50): List<SyncQueueEntity>

    /** Count pending items — used to decide whether to schedule a sync. */
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(item: SyncQueueEntity): Long

    /**
     * Mark accepted items as SYNCED by their local Room ID.
     *
     * ⚡ AUDIT FIX (v7): Uses LOCAL id (Room auto-increment), NOT the backend server ID.
     * The backend echoes local_id in the response so we can find the right row.
     * markSyncedByBackendId() was the bug — backend IDs and local IDs are different numbers.
     */
    @Query("UPDATE sync_queue SET status = 'SYNCED', syncedAt = :syncedAt WHERE id IN (:localIds)")
    suspend fun markSyncedByLocalId(localIds: List<Long>, syncedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'QUARANTINED' WHERE id IN (:localIds)")
    suspend fun markQuarantinedByLocalId(localIds: List<Long>)

    @Query("UPDATE sync_queue SET status = 'SYNCING' WHERE id IN (:localIds)")
    suspend fun markSyncing(localIds: List<Long>)

    @Query("UPDATE sync_queue SET status = 'FAILED', retryCount = retryCount + 1 WHERE id IN (:localIds)")
    suspend fun markFailed(localIds: List<Long>)

    /** Reset SYNCING → PENDING after a crash/interrupt to prevent stuck items.
     *  Only resets items that were never processed (syncedAt IS NULL). */
    @Query("UPDATE sync_queue SET status = 'PENDING' WHERE status = 'SYNCING' AND syncedAt IS NULL")
    suspend fun resetStuckSyncing()

    @Query("DELETE FROM sync_queue WHERE status = 'SYNCED' AND syncedAt < :beforeMillis")
    suspend fun pruneOldSynced(beforeMillis: Long)
}
