package com.souigat.mobile.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.local.dao.SyncQueueDao
import com.souigat.mobile.data.remote.api.SyncApi
import com.souigat.mobile.data.remote.dto.SyncBatchRequest
import com.souigat.mobile.data.remote.dto.SyncItemDto
import com.souigat.mobile.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncDataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenManager: TokenManager,
    private val syncQueueDao: SyncQueueDao,
    private val syncApi: SyncApi
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.i("SyncDataWorker started.")

        // No authenticated session: keep queue intact and skip network calls.
        if (tokenManager.getAccessToken().isNullOrBlank() || tokenManager.getRefreshToken().isNullOrBlank()) {
            Timber.i("No authenticated session found. Skipping sync run.")
            return Result.success()
        }

        // Step 1: Recover items stuck in SYNCING from a previous mid-flight crash
        syncQueueDao.resetStuckSyncing()

        val prefs = applicationContext.getSharedPreferences("sync_worker_prefs", Context.MODE_PRIVATE)
        var round = 0

        while (round < Constants.MAX_SYNC_DRAIN_ROUNDS) {
            val pendingItems = syncQueueDao.getPendingBatch(limit = Constants.SYNC_BATCH_SIZE)
            if (pendingItems.isEmpty()) {
                Timber.i("Sync queue is empty after %d round(s).", round)
                syncQueueDao.pruneOldSynced(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
                return Result.success()
            }

            round++
            Timber.i(
                "Sync round %d/%d processing %d pending item(s).",
                round,
                Constants.MAX_SYNC_DRAIN_ROUNDS,
                pendingItems.size
            )

            syncQueueDao.markSyncing(pendingItems.map { it.id })
            val groupedByTrip = pendingItems.groupBy { it.tripId }
            var shouldRetry = false

            for ((tripId, items) in groupedByTrip) {
                val syncSucceeded = syncTripBatch(tripId = tripId, items = items, prefs = prefs)
                if (!syncSucceeded) {
                    shouldRetry = true
                }
            }

            if (shouldRetry) {
                syncQueueDao.pruneOldSynced(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
                return Result.retry()
            }
        }

        Timber.w(
            "Sync worker reached max drain rounds (%d). Remaining items will be handled on the next trigger.",
            Constants.MAX_SYNC_DRAIN_ROUNDS
        )
        syncQueueDao.pruneOldSynced(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
        return Result.success()
    }

    private suspend fun syncTripBatch(
        tripId: Long,
        items: List<com.souigat.mobile.data.local.entity.SyncQueueEntity>,
        prefs: android.content.SharedPreferences
    ): Boolean {
        return try {
            val dtoList = items.map { entity ->
                SyncItemDto(
                    type = entity.itemType,
                    idempotencyKey = entity.idempotencyKey,
                    localId = entity.id,
                    payload = Json.parseToJsonElement(entity.payload)
                )
            }

            val request = SyncBatchRequest(
                tripId = tripId,
                resumeFrom = prefs.getLong("resume_from_$tripId", 0L),
                items = dtoList
            )

            Timber.i("Posting Sync batch to Django for Trip %d (%d items)...", tripId, dtoList.size)
            val response = syncApi.syncBatch(request)

            if (!response.isSuccessful) {
                Timber.e("Django returned HTTP %d for Trip %d batch.", response.code(), tripId)
                return false
            }

            val batchResponse = response.body()
            if (batchResponse != null) {
                val syncedIds = mutableListOf<Long>()
                val quarantinedIds = mutableListOf<Long>()

                for (itemResp in batchResponse.items) {
                    val respLocalId = itemResp.localId
                        ?: itemResp.index?.let { index -> dtoList.getOrNull(index)?.localId }
                        ?: continue

                    when (itemResp.status) {
                        "accepted", "duplicate" -> syncedIds += respLocalId
                        "quarantined" -> quarantinedIds += respLocalId
                        else -> Timber.w("Unknown sync status returned from server: %s", itemResp.status)
                    }
                }

                if (syncedIds.isNotEmpty()) {
                    syncQueueDao.markSyncedByLocalId(syncedIds)
                }
                if (quarantinedIds.isNotEmpty()) {
                    syncQueueDao.markQuarantinedByLocalId(quarantinedIds)
                }
            }

            prefs.edit().remove("resume_from_$tripId").apply()
            true
        } catch (e: Exception) {
            Timber.e(e, "Exception thrown during IO transmission for Trip %d batch.", tripId)
            false
        }
    }
}
