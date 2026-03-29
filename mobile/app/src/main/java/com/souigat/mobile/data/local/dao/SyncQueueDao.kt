package com.souigat.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.souigat.mobile.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

data class SyncingTypeCount(
    val itemType: String,
    val count: Int,
)

@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    fun observePending(): Flow<List<SyncQueueEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('PENDING', 'SYNCING', 'FAILED')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'QUARANTINED'")
    fun observeQuarantinedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'SYNCED'")
    fun observeSyncedCount(): Flow<Int>

    @Query("SELECT MAX(syncedAt) FROM sync_queue WHERE status = 'SYNCED'")
    fun observeLastSyncedAt(): Flow<Long?>

    @Query(
        "SELECT * FROM sync_queue " +
            "WHERE status IN ('PENDING', 'FAILED') " +
            "AND nextAttemptAt <= :nowMillis " +
            "ORDER BY createdAt ASC LIMIT :limit"
    )
    suspend fun getPendingBatch(nowMillis: Long, limit: Int = 50): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('PENDING', 'FAILED')")
    suspend fun getPendingCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueAll(items: List<SyncQueueEntity>): List<Long>

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

    @Query("UPDATE sync_queue SET status = 'PENDING' WHERE status = 'SYNCING' AND syncedAt IS NULL")
    suspend fun resetStuckSyncing(): Int

    @Query("UPDATE sync_queue SET nextAttemptAt = :nowMillis WHERE status = 'FAILED'")
    suspend fun expediteFailed(nowMillis: Long = System.currentTimeMillis()): Int

    @Query(
        "UPDATE sync_queue " +
            "SET status = 'PENDING', retryCount = 0, deadLetterReason = NULL, lastErrorCode = NULL, lastErrorMessage = NULL, " +
            "nextAttemptAt = :nowMillis " +
            "WHERE status = 'QUARANTINED' AND deadLetterReason IN ('permission_denied')"
    )
    suspend fun retryRecoverableQuarantined(nowMillis: Long = System.currentTimeMillis()): Int

    @Query(
        "UPDATE sync_queue " +
            "SET status = 'PENDING', retryCount = 0, deadLetterReason = NULL, lastErrorCode = NULL, lastErrorMessage = NULL, " +
            "nextAttemptAt = :nowMillis " +
            "WHERE status = 'QUARANTINED' " +
            "AND itemType IN ('passenger_ticket', 'cargo_ticket', 'cargo_status', 'expense', 'trip_status')"
    )
    suspend fun retryAllOperationalQuarantined(nowMillis: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM sync_queue WHERE status = 'SYNCED' AND syncedAt < :beforeMillis")
    suspend fun pruneOldSynced(beforeMillis: Long)
}
