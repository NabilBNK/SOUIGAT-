package com.souigat.mobile.worker

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.souigat.mobile.data.firebase.FirebaseSessionManager
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.dao.SyncQueueDao
import com.souigat.mobile.data.local.entity.SyncQueueEntity
import com.souigat.mobile.data.remote.api.SyncApi
import com.souigat.mobile.data.remote.dto.SyncBatchRequest
import com.souigat.mobile.data.remote.dto.SyncItemDto
import com.souigat.mobile.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.io.IOException
import kotlin.random.Random

@HiltWorker
class SyncDataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenManager: TokenManager,
    private val syncQueueDao: SyncQueueDao,
    private val tripDao: TripDao,
    private val firebaseSessionManager: FirebaseSessionManager,
    private val firestore: FirebaseFirestore,
    private val syncApi: SyncApi,
) : CoroutineWorker(appContext, workerParams) {

    private enum class SyncAction {
        SYNCED,
        RETRY,
        QUARANTINED,
    }

    private data class ItemSyncResult(
        val action: SyncAction,
        val errorCode: String? = null,
        val errorMessage: String? = null,
        val deadLetterReason: String? = null,
    )

    private data class QuarantineRecord(
        val localId: Long,
        val reason: String,
        val errorCode: String?,
        val errorMessage: String?,
    )

    private data class TripScope(
        val serverTripId: Long,
        val conductorId: Int,
        val officeScopeIds: List<Int>,
    )

    private val tripScopeCache = mutableMapOf<Long, TripScope>()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        Timber.i("SyncDataWorker started.")

        if (tokenManager.getUserId() == null) {
            Timber.i("No authenticated session found. Skipping sync run.")
            return Result.success()
        }

        val firebaseReady = firebaseSessionManager.ensureSignedIn()
        if (!firebaseReady) {
            Timber.w("Firebase session is not ready. Continuing with backend-first sync only.")
        }

        val now = System.currentTimeMillis()
        val stuckCount = syncQueueDao.countStuckSyncing()
        if (stuckCount > 0) {
            val oldestCreatedAt = syncQueueDao.oldestStuckSyncingCreatedAt()
            val oldestAgeMs = oldestCreatedAt?.let { now - it }
            val typeBreakdown = syncQueueDao.stuckSyncingTypeCounts()
                .joinToString(separator = ",") { "${it.itemType}:${it.count}" }

            Timber.w(
                "SyncDataWorker: resetting %d stuck syncing rows. oldestAgeMs=%s types=%s",
                stuckCount,
                oldestAgeMs?.toString() ?: "n/a",
                typeBreakdown.ifBlank { "n/a" },
            )
        }
        val resetCount = syncQueueDao.resetStuckSyncing()
        if (resetCount > 0) {
            Timber.i("SyncDataWorker: reset %d stuck syncing row(s) to pending.", resetCount)
        }
        var round = 0

        while (round < Constants.MAX_SYNC_DRAIN_ROUNDS) {
            val pendingItems = syncQueueDao.getPendingBatch(
                nowMillis = System.currentTimeMillis(),
                limit = Constants.SYNC_BATCH_SIZE,
            )
            if (pendingItems.isEmpty()) {
                Timber.i("Sync queue is empty after %d round(s).", round)
                syncQueueDao.pruneOldSynced(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
                return Result.success()
            }

            round++
            val typeBreakdown = pendingItems.groupingBy { it.itemType }.eachCount()
            Timber.i(
                "Sync round %d/%d processing %d pending item(s). breakdown=%s",
                round,
                Constants.MAX_SYNC_DRAIN_ROUNDS,
                pendingItems.size,
                typeBreakdown,
            )

            syncQueueDao.markSyncing(pendingItems.map { it.id })

            val syncedIds = mutableListOf<Long>()
            val quarantineRecords = mutableListOf<QuarantineRecord>()
            var shouldRetry = false

            for (item in pendingItems) {
                val result = syncItemToFirebase(item, firebaseReady)
                when (result.action) {
                    SyncAction.SYNCED -> syncedIds += item.id
                    SyncAction.QUARANTINED -> {
                        quarantineRecords += QuarantineRecord(
                            localId = item.id,
                            reason = result.deadLetterReason ?: "invalid_payload",
                            errorCode = result.errorCode,
                            errorMessage = result.errorMessage,
                        )
                    }
                    SyncAction.RETRY -> {
                        val nextRetryCount = item.retryCount + 1
                        if (nextRetryCount >= Constants.SYNC_MAX_ATTEMPTS) {
                            quarantineRecords += QuarantineRecord(
                                localId = item.id,
                                reason = "retry_limit_exceeded",
                                errorCode = result.errorCode,
                                errorMessage = result.errorMessage,
                            )
                        } else {
                            syncQueueDao.markFailed(
                                localId = item.id,
                                nextAttemptAt = nextAttemptAtMillis(nextRetryCount),
                                errorCode = result.errorCode,
                                errorMessage = result.errorMessage,
                            )
                            shouldRetry = true
                        }
                    }
                }
            }

            if (syncedIds.isNotEmpty()) {
                syncQueueDao.markSyncedByLocalId(syncedIds)
            }
            if (quarantineRecords.isNotEmpty()) {
                quarantineRecords.forEach { record ->
                    syncQueueDao.markQuarantinedByLocalId(
                        localIds = listOf(record.localId),
                        reason = record.reason,
                        errorCode = record.errorCode,
                        errorMessage = record.errorMessage,
                    )
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

    private suspend fun syncItemToFirebase(item: SyncQueueEntity, firebaseReady: Boolean): ItemSyncResult {
        return try {
            val payload = JSONObject(item.payload)

            when (item.itemType) {
                "passenger_ticket" -> {
                    if (!firebaseReady) {
                        return syncItemToBackend(item, itemType = "passenger_ticket")
                    }

                    val tripScope = resolveTripScope(item.tripId)
                    if (tripScope == null) {
                        Timber.w(
                            "SyncDataWorker: backend passenger ticket synced but trip scope missing for tripRefId=%d",
                            item.tripId,
                        )
                        return ItemSyncResult(
                            action = SyncAction.RETRY,
                            errorCode = "passenger_ticket_missing_trip_scope",
                            errorMessage = "Missing trip scope for passenger ticket mirror write.",
                        )
                    }

                    return runCatching {
                        writePassengerTicketMirror(item, payload, tripScope)
                        ItemSyncResult(action = SyncAction.SYNCED)
                    }.getOrElse { mirrorError ->
                        Timber.w(
                            mirrorError,
                            "SyncDataWorker: passenger ticket mirror write failed. localId=%d",
                            item.id,
                        )
                        ItemSyncResult(
                            action = SyncAction.RETRY,
                            errorCode = "passenger_ticket_mirror_write_failed",
                            errorMessage = mirrorError.message,
                        )
                    }
                }

                "expense" -> {
                    if (!firebaseReady) {
                        return syncItemToBackend(item, itemType = "expense")
                    }

                    val tripScope = resolveTripScope(item.tripId)
                    if (tripScope == null) {
                        Timber.w(
                            "SyncDataWorker: backend expense synced but trip scope missing for tripRefId=%d",
                            item.tripId,
                        )
                        return ItemSyncResult(
                            action = SyncAction.RETRY,
                            errorCode = "expense_missing_trip_scope",
                            errorMessage = "Missing trip scope for expense mirror write.",
                        )
                    }

                    return runCatching {
                        writeTripExpenseMirror(item, payload, tripScope)
                        ItemSyncResult(action = SyncAction.SYNCED)
                    }.getOrElse { mirrorError ->
                        Timber.w(
                            mirrorError,
                            "SyncDataWorker: expense mirror write failed. localId=%d",
                            item.id,
                        )
                        ItemSyncResult(
                            action = SyncAction.RETRY,
                            errorCode = "expense_mirror_write_failed",
                            errorMessage = mirrorError.message,
                        )
                    }
                }

                "trip_status" -> {
                    val backendResult = syncItemToBackend(item, itemType = "trip_status")
                    if (backendResult.action != SyncAction.SYNCED) {
                        return backendResult
                    }

                    if (!firebaseReady) {
                        return ItemSyncResult(action = SyncAction.SYNCED)
                    }

                    val tripScope = resolveTripScope(item.tripId)
                    if (tripScope == null) {
                        Timber.w(
                            "SyncDataWorker: backend trip status synced but trip scope missing for mirror tripRefId=%d",
                            item.tripId,
                        )
                        return ItemSyncResult(action = SyncAction.SYNCED)
                    }

                    runCatching {
                        writeTripStatusMirror(item, payload, tripScope)
                    }.onFailure { mirrorError ->
                        Timber.w(
                            mirrorError,
                            "SyncDataWorker: trip status mirror patch failed after backend sync. localId=%d",
                            item.id,
                        )
                    }

                    ItemSyncResult(action = SyncAction.SYNCED)
                }

                "cargo_ticket" -> {
                    if (!firebaseReady) {
                        return syncItemToBackend(item, itemType = "cargo_ticket")
                    }

                    val tripScope = resolveTripScope(item.tripId)
                    if (tripScope == null) {
                        Timber.w(
                            "SyncDataWorker: backend cargo ticket synced but trip scope missing for tripRefId=%d",
                            item.tripId,
                        )
                        return ItemSyncResult(
                            action = SyncAction.RETRY,
                            errorCode = "cargo_ticket_missing_trip_scope",
                            errorMessage = "Missing trip scope for cargo mirror write.",
                        )
                    }

                    return runCatching {
                        writeCargoTicketMirror(item, payload, tripScope)
                        ItemSyncResult(action = SyncAction.SYNCED)
                    }.getOrElse { mirrorError ->
                        Timber.w(
                            mirrorError,
                            "SyncDataWorker: cargo mirror write failed. localId=%d",
                            item.id,
                        )
                        ItemSyncResult(
                            action = SyncAction.RETRY,
                            errorCode = "cargo_ticket_mirror_write_failed",
                            errorMessage = mirrorError.message,
                        )
                    }
                }

                else -> {
                    Timber.w("Unsupported sync item type=%s localId=%d", item.itemType, item.id)
                    ItemSyncResult(
                        action = SyncAction.QUARANTINED,
                        errorCode = "unsupported_item_type",
                        errorMessage = "Unsupported sync item type=${item.itemType}",
                        deadLetterReason = "invalid_payload",
                    )
                }
            }
        } catch (error: FirebaseFirestoreException) {
            if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Timber.w(error, "Firestore permission denied for localId=%d", item.id)
                ItemSyncResult(
                    action = SyncAction.RETRY,
                    errorCode = "permission_denied",
                    errorMessage = error.message,
                )
            } else {
                Timber.e(error, "Firestore sync retry needed for localId=%d", item.id)
                ItemSyncResult(
                    action = SyncAction.RETRY,
                    errorCode = "firestore_${error.code.name.lowercase()}",
                    errorMessage = error.message,
                )
            }
        } catch (error: IllegalStateException) {
            Timber.w(error, "Permanent sync validation error for localId=%d", item.id)
            ItemSyncResult(
                action = SyncAction.QUARANTINED,
                errorCode = "invalid_state",
                errorMessage = error.message,
                deadLetterReason = "invalid_transition",
            )
        } catch (error: Exception) {
            Timber.e(error, "Exception during Firebase sync for localId=%d", item.id)
            ItemSyncResult(
                action = SyncAction.RETRY,
                errorCode = "unexpected_exception",
                errorMessage = error.message,
            )
        }
    }

    private suspend fun syncItemToBackend(item: SyncQueueEntity, itemType: String): ItemSyncResult {
        return try {
            val payloadElement = json.parseToJsonElement(item.payload)
            val request = SyncBatchRequest(
                tripId = item.tripId,
                resumeFrom = 0,
                items = listOf(
                    SyncItemDto(
                        type = itemType,
                        idempotencyKey = item.idempotencyKey,
                        localId = item.id,
                        payload = payloadElement,
                    )
                )
            )

            val response = syncApi.syncBatch(request)
            if (!response.isSuccessful) {
                val code = response.code()
                val errorBody = response.errorBody()?.string()
                return when {
                    code >= 500 || code == 408 || code == 429 -> ItemSyncResult(
                        action = SyncAction.RETRY,
                        errorCode = "${itemType}_backend_http_$code",
                        errorMessage = errorBody,
                    )

                    else -> ItemSyncResult(
                        action = SyncAction.QUARANTINED,
                        errorCode = "${itemType}_backend_http_$code",
                        errorMessage = errorBody,
                        deadLetterReason = "backend_rejected",
                    )
                }
            }

            val body = response.body()
                ?: return ItemSyncResult(
                    action = SyncAction.RETRY,
                    errorCode = "${itemType}_backend_empty_body",
                    errorMessage = "Backend sync response body is null.",
                )

            val itemResult = body.items.firstOrNull { it.localId == item.id }
                ?: body.items.firstOrNull()
                ?: return ItemSyncResult(
                    action = SyncAction.RETRY,
                    errorCode = "${itemType}_backend_missing_item_result",
                    errorMessage = "Backend sync response does not include item status.",
                )

            when (itemResult.status) {
                "accepted", "duplicate" -> ItemSyncResult(action = SyncAction.SYNCED)
                "quarantined" -> ItemSyncResult(
                    action = SyncAction.QUARANTINED,
                    errorCode = "${itemType}_backend_quarantined",
                    errorMessage = "Backend quarantined $itemType item.",
                    deadLetterReason = "backend_quarantined",
                )

                else -> ItemSyncResult(
                    action = SyncAction.RETRY,
                    errorCode = "${itemType}_backend_unknown_status",
                    errorMessage = "Unknown backend sync status=${itemResult.status}",
                )
            }
        } catch (error: IOException) {
            ItemSyncResult(
                action = SyncAction.RETRY,
                errorCode = "${itemType}_backend_io_exception",
                errorMessage = error.message,
            )
        } catch (error: Exception) {
            ItemSyncResult(
                action = SyncAction.RETRY,
                errorCode = "${itemType}_backend_sync_exception",
                errorMessage = error.message,
            )
        }
    }

    private fun nextAttemptAtMillis(nextRetryCount: Int): Long {
        val exponential = Constants.SYNC_BASE_BACKOFF_MS * (1L shl minOf(nextRetryCount, 16))
        val clamped = minOf(exponential, Constants.SYNC_MAX_BACKOFF_MS)
        val jitterWindow = (clamped * 0.2).toLong()
        val jitter = if (jitterWindow > 0) Random.nextLong(-jitterWindow, jitterWindow + 1) else 0L
        return System.currentTimeMillis() + clamped + jitter
    }

    private suspend fun resolveTripScope(tripRefId: Long): TripScope? {
        tripScopeCache[tripRefId]?.let { return it }

        val localTrip = tripDao.getByLocalOrServerId(tripRefId)
        if (localTrip == null) {
            Timber.w("SyncDataWorker: missing local trip for tripRefId=%d", tripRefId)
            return null
        }

        val serverTripId = localTrip.serverId ?: tripRefId
        var conductorId = localTrip.conductorId.toInt()
        var officeScopeIds: List<Int> = emptyList()

        runCatching {
            firestore.collection("trip_mirror_v1")
                .document(serverTripId.toString())
                .get()
                .await()
        }.onSuccess { snapshot ->
            if (snapshot.exists()) {
                conductorId = snapshot.getLong("conductor_id")?.toInt() ?: conductorId
                officeScopeIds = (snapshot.get("office_scope_ids") as? List<*>)
                    ?.mapNotNull { raw ->
                        when (raw) {
                            is Int -> raw
                            is Long -> raw.toInt()
                            is Double -> raw.toInt()
                            is String -> raw.toIntOrNull()
                            else -> null
                        }
                    }
                    .orEmpty()
            }
        }.onFailure { error ->
            Timber.w(error, "SyncDataWorker: unable to read trip mirror metadata for trip=%d", serverTripId)
        }

        val scope = TripScope(
            serverTripId = serverTripId,
            conductorId = conductorId,
            officeScopeIds = officeScopeIds,
        )
        tripScopeCache[tripRefId] = scope
        return scope
    }

    private suspend fun writePassengerTicketMirror(
        item: SyncQueueEntity,
        payload: JSONObject,
        tripScope: TripScope,
    ) {
        val nowIso = Instant.now().toString()
        val createdAtIso = Instant.ofEpochMilli(item.createdAt).toString()
        val createdById = tokenManager.getUserId() ?: tripScope.conductorId
        val createdByName = tokenManager.getFullName().orEmpty()
        val conductorIdForWrite = tokenManager.getUserId() ?: tripScope.conductorId

        val documentData = mutableMapOf<String, Any>(
            "entity" to "passenger_ticket",
            "id" to syntheticMirrorId("passenger", item.idempotencyKey),
            "trip_id" to tripScope.serverTripId,
            "ticket_number" to payload.optString("ticket_number", "PT-${tripScope.serverTripId}-${item.id}"),
            "passenger_name" to payload.optString("passenger_name", "Passager"),
            "seat_number" to payload.optString("seat_number", ""),
            "price" to payload.optLong("price", 0L),
            "currency" to payload.optString("currency", "DZD"),
            "payment_source" to payload.optString("payment_source", "cash"),
            "status" to payload.optString("status", "active"),
            "created_by_id" to createdById,
            "created_by_name" to createdByName,
            "conductor_id" to conductorIdForWrite,
            "office_scope_ids" to tripScope.officeScopeIds,
            "source_created_at" to createdAtIso,
            "source_updated_at" to nowIso,
            "is_deleted" to false,
            "sync_version" to 1,
            "sync_origin" to "mobile_firebase_direct",
            "idempotency_key" to item.idempotencyKey,
        )

        createMirrorDocumentIdempotent(
            collection = "passenger_ticket_mirror_v1",
            documentId = "pt_${item.idempotencyKey}",
            documentData = documentData,
            _idempotencyKey = item.idempotencyKey,
        )
    }

    private suspend fun writeTripExpenseMirror(
        item: SyncQueueEntity,
        payload: JSONObject,
        tripScope: TripScope,
    ) {
        val nowIso = Instant.now().toString()
        val createdAtIso = Instant.ofEpochMilli(item.createdAt).toString()
        val createdById = tokenManager.getUserId() ?: tripScope.conductorId
        val createdByName = tokenManager.getFullName().orEmpty()
        val conductorIdForWrite = tokenManager.getUserId() ?: tripScope.conductorId

        val documentData = mutableMapOf<String, Any>(
            "entity" to "trip_expense",
            "id" to syntheticMirrorId("expense", item.idempotencyKey),
            "trip_id" to tripScope.serverTripId,
            "description" to payload.optString("description", ""),
            "category" to payload.optString("category", "other"),
            "amount" to payload.optLong("amount", 0L),
            "currency" to payload.optString("currency", "DZD"),
            "created_by_id" to createdById,
            "created_by_name" to createdByName,
            "conductor_id" to conductorIdForWrite,
            "office_scope_ids" to tripScope.officeScopeIds,
            "source_created_at" to createdAtIso,
            "source_updated_at" to nowIso,
            "is_deleted" to false,
            "sync_version" to 1,
            "sync_origin" to "mobile_firebase_direct",
            "idempotency_key" to item.idempotencyKey,
        )

        createMirrorDocumentIdempotent(
            collection = "trip_expense_mirror_v1",
            documentId = "exp_${item.idempotencyKey}",
            documentData = documentData,
            _idempotencyKey = item.idempotencyKey,
        )
    }

    private suspend fun writeCargoTicketMirror(
        item: SyncQueueEntity,
        payload: JSONObject,
        tripScope: TripScope,
    ) {
        val nowIso = Instant.now().toString()
        val createdAtIso = Instant.ofEpochMilli(item.createdAt).toString()
        val createdById = tokenManager.getUserId() ?: tripScope.conductorId
        val createdByName = tokenManager.getFullName().orEmpty()
        val conductorIdForWrite = tokenManager.getUserId() ?: tripScope.conductorId

        val documentData = mutableMapOf<String, Any>(
            "entity" to "cargo_ticket",
            "id" to syntheticMirrorId("cargo", item.idempotencyKey),
            "trip_id" to tripScope.serverTripId,
            "ticket_number" to payload.optString("ticket_number", "CT-${tripScope.serverTripId}-${item.id}"),
            "sender_name" to payload.optString("sender_name", ""),
            "sender_phone" to payload.optString("sender_phone", ""),
            "receiver_name" to payload.optString("receiver_name", ""),
            "receiver_phone" to payload.optString("receiver_phone", ""),
            "cargo_tier" to payload.optString("cargo_tier", "small"),
            "description" to payload.optString("description", ""),
            "price" to payload.optLong("price", 0L),
            "currency" to payload.optString("currency", "DZD"),
            "payment_source" to payload.optString("payment_source", "prepaid"),
            "status" to payload.optString("status", "created"),
            "created_by_id" to createdById,
            "created_by_name" to createdByName,
            "conductor_id" to conductorIdForWrite,
            "office_scope_ids" to tripScope.officeScopeIds,
            "source_created_at" to createdAtIso,
            "source_updated_at" to nowIso,
            "is_deleted" to false,
            "sync_version" to 1,
            "sync_origin" to "mobile_firebase_direct",
            "idempotency_key" to item.idempotencyKey,
        )

        createMirrorDocumentIdempotent(
            collection = "cargo_ticket_mirror_v1",
            documentId = "ct_${item.idempotencyKey}",
            documentData = documentData,
            _idempotencyKey = item.idempotencyKey,
        )
    }

    private suspend fun createMirrorDocumentIdempotent(
        collection: String,
        documentId: String,
        documentData: Map<String, Any>,
        _idempotencyKey: String,
    ) {
        val ref = firestore.collection(collection).document(documentId)
        val existing = ref.get().await()
        if (existing.exists()) {
            return
        }

        ref.set(documentData).await()
    }

    private suspend fun writeTripStatusMirror(
        item: SyncQueueEntity,
        payload: JSONObject,
        tripScope: TripScope,
    ) {
        val newStatus = payload.optString("status", "").trim()
        if (newStatus != "in_progress" && newStatus != "completed") {
            throw IllegalStateException("Unsupported trip_status payload status=$newStatus")
        }

        val transitionAt = payload.optLong("transition_at", System.currentTimeMillis())
        val transitionIso = Instant.ofEpochMilli(transitionAt).toString()

        val tripDocRef = firestore.collection("trip_mirror_v1").document(tripScope.serverTripId.toString())
        val snapshot = tripDocRef.get().await()
        val currentStatus = snapshot.getString("status")

        if (currentStatus == newStatus) {
            return
        }

        val validTransition = when (currentStatus) {
            "scheduled" -> newStatus == "in_progress"
            "in_progress" -> newStatus == "completed"
            else -> false
        }

        if (!validTransition) {
            throw IllegalStateException("Invalid trip status transition $currentStatus -> $newStatus")
        }

        val updateData = mutableMapOf<String, Any>(
            "status" to newStatus,
            "source_updated_at" to transitionIso,
        )
        if (newStatus == "completed") {
            updateData["arrival_ts"] = transitionAt
            updateData["arrival_datetime"] = transitionIso
        }

        tripDocRef.set(updateData, SetOptions.merge()).await()
    }

    private fun syntheticMirrorId(prefix: String, key: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest("$prefix:$key".toByteArray())
        var value = 0L
        for (idx in 0 until 8) {
            value = (value shl 8) or (digest[idx].toLong() and 0xff)
        }
        return value and Long.MAX_VALUE
    }
}
