package com.souigat.mobile.data.repository

import androidx.room.withTransaction
import com.souigat.mobile.data.local.SouigatDatabase
import com.souigat.mobile.data.local.dao.ExpenseDao
import com.souigat.mobile.data.local.dao.SyncQueueDao
import com.souigat.mobile.data.local.entity.ExpenseEntity
import com.souigat.mobile.data.local.entity.SyncQueueEntity
import com.souigat.mobile.domain.model.SyncStatus
import com.souigat.mobile.domain.repository.ExpenseRepository
import com.souigat.mobile.worker.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val db: SouigatDatabase,
    private val expenseDao: ExpenseDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncScheduler: SyncScheduler
) : ExpenseRepository {

    override suspend fun createExpense(
        tripId: Long,
        amount: Long,
        currency: String,
        category: String,
        description: String
    ): Result<ExpenseEntity> = withContext(Dispatchers.IO) {
        val localTripId = resolveLocalTripId(tripId)
            ?: run {
                Timber.w("createExpense aborted: trip not found locally. requestedTripId=%d", tripId)
                return@withContext Result.failure(buildMissingTripException(tripId))
            }

        return@withContext try {
            val idempotencyKey = UUID.randomUUID().toString()
            val savedEntity = db.withTransaction {
                val entity = ExpenseEntity(
                    serverId = null,
                    tripId = localTripId,
                    idempotencyKey = idempotencyKey,
                    amount = amount,
                    currency = currency,
                    category = category,
                    description = description,
                    status = "active"
                )

                val localId = expenseDao.upsert(entity)
                val newSavedEntity = entity.copy(id = localId)

                // Prepare SyncQueue payload
                val jsonPayload = JSONObject().apply {
                    put("idempotencyKey", idempotencyKey)
                    put("trip", tripId)
                    put("amount", amount)
                    put("money_scale", "base_unit")
                    put("currency", currency)
                    put("category", category)
                    put("description", description)
                }.toString()

                val syncItem = SyncQueueEntity(
                    tripId = tripId,
                    itemType = "expense",
                    payload = jsonPayload,
                    idempotencyKey = idempotencyKey,
                    status = SyncStatus.PENDING
                )
                syncQueueDao.enqueue(syncItem)
                
                newSavedEntity
            }
            syncScheduler.triggerOneTimeSync()
            Result.success(savedEntity)
        } catch (e: Exception) {
            if (isForeignKeyConstraint(e)) {
                Timber.w(e, "createExpense FK violation. requestedTripId=%d localTripId=%d", tripId, localTripId)
                Result.failure(buildMissingTripException(tripId, e))
            } else {
                Timber.e(e, "createExpense failed. requestedTripId=%d localTripId=%d", tripId, localTripId)
                Result.failure(e)
            }
        }
    }

    override fun observeExpensesByTrip(tripId: Long): Flow<List<ExpenseEntity>> {
        return expenseDao.observeByTripOrServerId(tripId)
    }

    override suspend fun getTotalExpenseForTrip(tripId: Long): Long = withContext(Dispatchers.IO) {
        return@withContext expenseDao.getTotalAmount(tripId) ?: 0L
    }

    private suspend fun resolveLocalTripId(requestedTripId: Long): Long? {
        val tripDao = db.tripDao()
        val resolved = tripDao.getByLocalOrServerId(requestedTripId)?.id

        Timber.d(
            "resolveLocalTripId(expense): requestedTripId=%d resolvedLocalTripId=%s",
            requestedTripId,
            resolved?.toString() ?: "null"
        )
        return resolved
    }

    private fun buildMissingTripException(tripId: Long, cause: Throwable? = null): Exception {
        return IllegalStateException(
            "Trajet introuvable localement (id=$tripId). Rafraichissez la liste des trajets puis reessayez.",
            cause
        )
    }

    private fun isForeignKeyConstraint(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message?.lowercase().orEmpty()
            if (
                message.contains("foreign key constraint failed")
                || message.contains("sqlite_constraint_foreignkey")
                || message.contains("code 787")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
