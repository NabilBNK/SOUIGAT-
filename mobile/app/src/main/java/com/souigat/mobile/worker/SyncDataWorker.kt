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
import com.souigat.mobile.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.json.JSONObject

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

    private enum class ItemSyncResult {
        SYNCED,
        RETRY,
        QUARANTINED,
    }

    private data class TripScope(
        val serverTripId: Long,
        val conductorId: Int,
        val officeScopeIds: List<Int>,
    )

    private val tripScopeCache = mutableMapOf<Long, TripScope>()

    override suspend fun doWork(): Result {
        Timber.i("SyncDataWorker started.")

        if (tokenManager.getUserId() == null) {
            Timber.i("No authenticated session found. Skipping sync run.")
            return Result.success()
        }

        if (!firebaseSessionManager.ensureSignedIn()) {
            Timber.w("Firebase session is not ready. Retry later.")
            return Result.retry()
        }

        syncQueueDao.resetStuckSyncing()
        syncQueueDao.requeueQuarantinedByType(listOf("passenger_ticket", "expense"))
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

            val syncedIds = mutableListOf<Long>()
            val quarantinedIds = mutableListOf<Long>()
            var shouldRetry = false

            for (item in pendingItems) {
                when (syncItemToFirebase(item)) {
                    ItemSyncResult.SYNCED -> syncedIds += item.id
                    ItemSyncResult.QUARANTINED -> quarantinedIds += item.id
                    ItemSyncResult.RETRY -> shouldRetry = true
                }
            }

            if (syncedIds.isNotEmpty()) {
                syncQueueDao.markSyncedByLocalId(syncedIds)
            }
            if (quarantinedIds.isNotEmpty()) {
                syncQueueDao.markQuarantinedByLocalId(quarantinedIds)
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

    private suspend fun syncItemToFirebase(item: SyncQueueEntity): ItemSyncResult {
        return try {
            val payload = JSONObject(item.payload)
            val tripScope = resolveTripScope(item.tripId)
                ?: return ItemSyncResult.RETRY

            when (item.itemType) {
                "passenger_ticket" -> {
                    writePassengerTicketMirror(item, payload, tripScope)
                    ItemSyncResult.SYNCED
                }

                "expense" -> {
                    writeTripExpenseMirror(item, payload, tripScope)
                    ItemSyncResult.SYNCED
                }

                "cargo_ticket" -> {
                    val role = tokenManager.getUserRole().orEmpty()
                    if (role == "conductor") {
                        Timber.w("Conductor cannot create cargo mirror entries directly. localId=%d", item.id)
                        ItemSyncResult.QUARANTINED
                    } else {
                        writeCargoTicketMirror(item, payload, tripScope)
                        ItemSyncResult.SYNCED
                    }
                }

                else -> {
                    Timber.w("Unsupported sync item type=%s localId=%d", item.itemType, item.id)
                    ItemSyncResult.QUARANTINED
                }
            }
        } catch (error: FirebaseFirestoreException) {
            if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Timber.w(error, "Firestore permission denied for localId=%d", item.id)
                ItemSyncResult.QUARANTINED
            } else {
                Timber.e(error, "Firestore sync retry needed for localId=%d", item.id)
                ItemSyncResult.RETRY
            }
        } catch (error: Exception) {
            Timber.e(error, "Exception during Firebase sync for localId=%d", item.id)
            ItemSyncResult.RETRY
        }
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

        firestore.collection("passenger_ticket_mirror_v1")
            .document("pt_${item.idempotencyKey}")
            .set(documentData, SetOptions.merge())
            .await()
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

        firestore.collection("trip_expense_mirror_v1")
            .document("exp_${item.idempotencyKey}")
            .set(documentData, SetOptions.merge())
            .await()
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

        firestore.collection("cargo_ticket_mirror_v1")
            .document("ct_${item.idempotencyKey}")
            .set(documentData, SetOptions.merge())
            .await()
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
