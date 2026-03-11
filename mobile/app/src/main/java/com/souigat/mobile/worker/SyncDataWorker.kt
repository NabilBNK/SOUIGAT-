package com.souigat.mobile.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.souigat.mobile.data.local.dao.SyncQueueDao
import com.souigat.mobile.data.remote.api.SyncApi
import com.souigat.mobile.data.remote.dto.SyncBatchRequest
import com.souigat.mobile.data.remote.dto.SyncItemDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncDataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncQueueDao: SyncQueueDao,
    private val syncApi: SyncApi
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.i("SyncDataWorker started.")

        // Step 1: Recover items stuck in SYNCING from a previous mid-flight crash
        syncQueueDao.resetStuckSyncing()

        // Step 2: Fetch PENDING items
        val pendingItems = syncQueueDao.getPendingBatch(limit = 50)
        if (pendingItems.isEmpty()) {
            Timber.i("Sync queue is empty. Worker finished.")
            return Result.success()
        }

        // Mark them as SYNCING immediately so concurrent sweeps ignore them
        val localIds = pendingItems.map { it.id }
        syncQueueDao.markSyncing(localIds)

        // Step 3: Aggregate structurally by TripId to avoid multi-trip lock constraints in backend
        val groupedByTrip = pendingItems.groupBy { it.tripId }

        var finalResult = Result.success()

        val prefs = applicationContext.getSharedPreferences("sync_worker_prefs", Context.MODE_PRIVATE)

        for ((tripId, items) in groupedByTrip) {
            try {
                // Map to SyncItemDto
                val dtoList = items.map { entity ->
                    val rawJsonStr = entity.payload
                    // Parse raw JSON into JsonElement for Retrofit
                    val jsonElement = Json.parseToJsonElement(rawJsonStr)

                    SyncItemDto(
                        type = entity.itemType,
                        idempotencyKey = entity.idempotencyKey,
                        localId = entity.id,
                        payload = jsonElement
                    )
                }

                // Retrieve the latest `resumeFrom` index stored by a previous mid-flight crash.
                val storedResumeFrom = prefs.getLong("resume_from_$tripId", 0L)

                val request = SyncBatchRequest(
                    tripId = tripId,
                    resumeFrom = storedResumeFrom,
                    items = dtoList
                )

                // Execute REST Sync Batch for this Trip
                Timber.i("Posting Sync batch to Django for Trip $tripId (${dtoList.size} items)...")
                val response = syncApi.syncBatch(request)

                if (response.isSuccessful) {
                    val batchResponse = response.body()
                    if (batchResponse != null) {
                        for (itemResp in batchResponse.items) {
                            val indexValue = itemResp.index
                            val respLocalId = itemResp.localId ?: if (indexValue != null) dtoList.getOrNull(indexValue)?.localId else null
                            if (respLocalId == null) continue

                            when (itemResp.status) {
                                "accepted", "duplicate" -> syncQueueDao.markSyncedByLocalId(listOf(respLocalId))
                                "quarantined" -> syncQueueDao.markQuarantinedByLocalId(listOf(respLocalId))
                                else -> Timber.w("Unknown sync status returned from server: ${itemResp.status}")
                            }
                        }
                        
                        // On success: clear the stale key for this trip
                        prefs.edit().remove("resume_from_$tripId").apply()
                    }
                } else {
                    Timber.e("Django returned HTTP ${response.code()} for Trip $tripId batch.")
                    
                    // resume_from is cleared on success above.
                    // On retry, next run starts at resumeFrom=0 (server handles idempotency via idempotencyKey).
                    
                    // If 403 happened and interceptor refresh failed, we are cleanly kicked back to retry via WorkManager backoffs
                    if (response.code() in 500..599) {
                        Timber.e("Server error detected. Marking for retry.")
                        finalResult = Result.retry()
                    } else if (response.code() == 403) {
                        Timber.e("403 Forbidden AFTER interceptor attempt. Returning to pending.")
                        finalResult = Result.retry()
                    } else {
                        // 400 Bad Request, etc. Usually shouldn't fail batch entirely but if it does:
                        Timber.e("Batch format rejection! Reverting to pending for manual review.")
                        finalResult = Result.retry()
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "Exception thrown during IO transmission for Trip $tripId batch.")
                finalResult = Result.retry()
            }
        }

        // Clean out ancient synchronizations
        syncQueueDao.pruneOldSynced(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))

        return finalResult
    }
}
