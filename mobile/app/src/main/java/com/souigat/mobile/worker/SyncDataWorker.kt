package com.souigat.mobile.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.souigat.mobile.data.firebase.FirebaseSessionManager
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.local.dao.SyncQueueDao
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.entity.SyncQueueEntity
import com.souigat.mobile.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import timber.log.Timber

@HiltWorker
class SyncDataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenManager: TokenManager,
    private val syncQueueDao: SyncQueueDao,
    private val tripDao: TripDao,
    private val firebaseSessionManager: FirebaseSessionManager,
    private val firestore: FirebaseFirestore,
) : CoroutineWorker(appContext, workerParams) {

    private class IdempotencyConflictException(message: String) : Exception(message)

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

    override suspend fun doWork(): Result {
        if (tokenManager.getUserId() == null) {
            return Result.success()
        }

        val firebaseReady = firebaseSessionManager.ensureSignedIn()
        if (!firebaseReady) {
            return Result.retry()
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

        // Recover rows quarantined only because of prior transient permission mismatches
        // (for example, rule/claim rollout timing). They can be retried safely by idempotency key.
        val recoveredQuarantined = syncQueueDao.retryRecoverableQuarantined()
        if (recoveredQuarantined > 0) {
            Timber.i(
                "SyncDataWorker: recovered %d permission_denied quarantine row(s) for retry.",
                recoveredQuarantined,
            )
        }

        var round = 0
        while (round < Constants.MAX_SYNC_DRAIN_ROUNDS) {
            val pendingItems = syncQueueDao.getPendingBatch(
                nowMillis = System.currentTimeMillis(),
                limit = Constants.SYNC_BATCH_SIZE,
            )

            if (pendingItems.isEmpty()) {
                syncQueueDao.pruneOldSynced(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
                return Result.success()
            }

            round++
            syncQueueDao.markSyncing(pendingItems.map { it.id })

            val syncedIds = mutableListOf<Long>()
            val quarantineRecords = mutableListOf<QuarantineRecord>()
            var shouldRetry = false

            for (item in pendingItems) {
                val result = syncItemToFirebase(item)
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
                            if (shouldKeepRetryingWithoutQuarantine(result.errorCode)) {
                                syncQueueDao.markFailed(
                                    localId = item.id,
                                    nextAttemptAt = System.currentTimeMillis() + Constants.SYNC_MAX_BACKOFF_MS,
                                    errorCode = result.errorCode,
                                    errorMessage = result.errorMessage,
                                )
                                shouldRetry = true
                            } else {
                                quarantineRecords += QuarantineRecord(
                                    localId = item.id,
                                    reason = "retry_limit_exceeded",
                                    errorCode = result.errorCode,
                                    errorMessage = result.errorMessage,
                                )
                            }
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

        syncQueueDao.pruneOldSynced(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
        return Result.success()
    }

    private suspend fun syncItemToFirebase(item: SyncQueueEntity): ItemSyncResult {
        return try {
            val payload = JSONObject(item.payload)
            val tripScope = resolveTripScope(item.tripId)
                ?: return ItemSyncResult(
                    action = SyncAction.RETRY,
                    errorCode = "missing_trip_scope",
                    errorMessage = "Missing trip scope for sync write.",
                )

            when (item.itemType) {
                "passenger_ticket" -> {
                    writePassengerTicketMirror(item, payload, tripScope)
                    ItemSyncResult(action = SyncAction.SYNCED)
                }

                "expense" -> {
                    writeTripExpenseMirror(item, payload, tripScope)
                    ItemSyncResult(action = SyncAction.SYNCED)
                }

                "trip_status" -> {
                    writeTripStatusMirror(item, payload, tripScope)
                    ItemSyncResult(action = SyncAction.SYNCED)
                }

                "cargo_ticket" -> {
                    writeCargoTicketMirror(item, payload, tripScope)
                    ItemSyncResult(action = SyncAction.SYNCED)
                }

                "cargo_status" -> {
                    val synced = writeCargoStatusMirror(payload, tripScope)
                    if (synced) {
                        ItemSyncResult(action = SyncAction.SYNCED)
                    } else {
                        ItemSyncResult(
                            action = SyncAction.RETRY,
                            errorCode = "cargo_status_missing_mirror",
                            errorMessage = "Cargo mirror document is not available yet for status update.",
                        )
                    }
                }

                else -> {
                    ItemSyncResult(
                        action = SyncAction.QUARANTINED,
                        errorCode = "unsupported_item_type",
                        errorMessage = "Unsupported sync item type=${item.itemType}",
                        deadLetterReason = "invalid_payload",
                    )
                }
            }
        } catch (error: IdempotencyConflictException) {
            ItemSyncResult(
                action = SyncAction.QUARANTINED,
                errorCode = "idempotency_conflict",
                errorMessage = error.message,
                deadLetterReason = "invalid_payload",
            )
        } catch (error: FirebaseFirestoreException) {
            if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                ItemSyncResult(
                    action = SyncAction.RETRY,
                    errorCode = "permission_denied",
                    errorMessage = error.message,
                )
            } else {
                ItemSyncResult(
                    action = SyncAction.RETRY,
                    errorCode = "firestore_${error.code.name.lowercase()}",
                    errorMessage = error.message,
                )
            }
        } catch (error: IllegalStateException) {
            ItemSyncResult(
                action = SyncAction.QUARANTINED,
                errorCode = "invalid_state",
                errorMessage = error.message,
                deadLetterReason = "invalid_transition",
            )
        } catch (error: Exception) {
            ItemSyncResult(
                action = SyncAction.RETRY,
                errorCode = "unexpected_exception",
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

    private fun shouldKeepRetryingWithoutQuarantine(errorCode: String?): Boolean {
        if (errorCode.isNullOrBlank()) return false
        return errorCode == "permission_denied" ||
            errorCode == "missing_trip_scope" ||
            errorCode == "cargo_status_missing_mirror" ||
            errorCode.startsWith("firestore_")
    }

    private suspend fun resolveTripScope(tripRefId: Long): TripScope? {
        tripScopeCache[tripRefId]?.let { return it }

        val localTrip = tripDao.getByLocalOrServerId(tripRefId) ?: return null

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
        )
    }

    private suspend fun createMirrorDocumentIdempotent(
        collection: String,
        documentId: String,
        documentData: Map<String, Any>,
    ) {
        val ref = firestore.collection(collection).document(documentId)
        val idempotencyKey = documentData["idempotency_key"]?.toString()
            ?: throw IdempotencyConflictException("Missing idempotency_key for $collection/$documentId")

        // Fast-path idempotency: if the deterministic mirror doc already exists with the same
        // idempotency key, treat this item as synced and skip a replay update write entirely.
        val existingBeforeWrite = runCatching { ref.get().await() }.getOrNull()
        if (existingBeforeWrite?.exists() == true) {
            val existingKey = existingBeforeWrite.getString("idempotency_key")
            if (existingKey == idempotencyKey) {
                return
            }
            throw IdempotencyConflictException(
                "Idempotency conflict for $collection/$documentId: expected=$idempotencyKey actual=${existingKey ?: "null"}",
            )
        }

        try {
            ref.set(documentData).await()
        } catch (error: FirebaseFirestoreException) {
            if (error.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw error
            }

            // If write permissions changed between retries, verify whether the mirror document
            // was already created by a previous successful attempt and acknowledge it as synced.
            val existingAfterDenied = runCatching { ref.get().await() }.getOrNull()
            val existingKey = existingAfterDenied?.getString("idempotency_key")
            if (existingAfterDenied?.exists() == true && existingKey == idempotencyKey) {
                Timber.i(
                    "SyncDataWorker: acknowledged pre-existing mirror doc after permission_denied. collection=%s docId=%s",
                    collection,
                    documentId,
                )
                return
            }

            throw error
        }
    }

    private suspend fun writeCargoStatusMirror(
        payload: JSONObject,
        tripScope: TripScope,
    ): Boolean {
        val cargoKey = payload.optString("cargo_idempotency_key", "").trim()
        if (cargoKey.isBlank()) {
            throw IllegalStateException("Missing cargo_idempotency_key for cargo status sync.")
        }

        val newStatus = payload.optString("status", "").trim()
        if (newStatus != "arrived" && newStatus != "delivered") {
            throw IllegalStateException("Unsupported cargo status payload status=$newStatus")
        }

        val transitionAt = payload.optLong("transition_at", System.currentTimeMillis())
        val transitionIso = Instant.ofEpochMilli(transitionAt).toString()
        val cargoDocRef = firestore.collection("cargo_ticket_mirror_v1").document("ct_$cargoKey")
        val cargoSnapshot = cargoDocRef.get().await()
        if (!cargoSnapshot.exists()) {
            return false
        }

        val currentStatus = cargoSnapshot.getString("status")?.trim()?.lowercase().orEmpty()
        if (currentStatus == newStatus) {
            return true
        }

        val validTransition = when (currentStatus) {
            "created", "loaded", "in_transit" -> true
            else -> false
        }
        if (!validTransition) {
            Timber.w(
                "SyncDataWorker: skipping stale cargo_status transition %s -> %s for cargo=%s",
                currentStatus,
                newStatus,
                cargoKey,
            )
            return true
        }

        val updateData = mutableMapOf<String, Any>(
            "status" to newStatus,
            "source_updated_at" to transitionIso,
        )

        if (newStatus == "delivered") {
            val deliveredAtIso = payload.optString("delivered_at", transitionIso).ifBlank { transitionIso }
            val deliveredById = payload.optLong(
                "delivered_by_id",
                (tokenManager.getUserId() ?: tripScope.conductorId).toLong(),
            )
            updateData["delivered_at"] = deliveredAtIso
            updateData["delivered_by_id"] = deliveredById
        }

        cargoDocRef.set(updateData, SetOptions.merge()).await()
        return true
    }

    private suspend fun writeTripStatusMirror(
        _item: SyncQueueEntity,
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
