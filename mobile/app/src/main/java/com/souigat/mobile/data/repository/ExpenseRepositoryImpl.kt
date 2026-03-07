package com.souigat.mobile.data.repository

import com.souigat.mobile.data.local.dao.ExpenseDao
import com.souigat.mobile.data.local.dao.SyncQueueDao
import com.souigat.mobile.data.local.SouigatDatabase
import com.souigat.mobile.data.local.entity.ExpenseEntity
import com.souigat.mobile.data.local.entity.SyncQueueEntity
import androidx.room.withTransaction
import com.souigat.mobile.domain.model.SyncStatus
import com.souigat.mobile.domain.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val db: SouigatDatabase,
    private val expenseDao: ExpenseDao,
    private val syncQueueDao: SyncQueueDao
) : ExpenseRepository {

    override suspend fun createExpense(
        tripId: Long,
        amount: Long,
        currency: String,
        category: String,
        description: String
    ): Result<ExpenseEntity> = withContext(Dispatchers.IO) {
        var retries = 0
        val maxRetries = 5
        var lastException: Exception? = null

        // Stable idempotency key generated ONCE per creation request
        val idempotencyKey = UUID.randomUUID().toString()

        while (retries < maxRetries) {
            try {
                val savedEntity = db.withTransaction {
                    val entity = ExpenseEntity(
                        serverId = null,
                        tripId = tripId,
                        idempotencyKey = idempotencyKey,
                        amount = amount,
                        currency = currency,
                        category = category,
                        description = description
                    )

                    val localId = expenseDao.upsert(entity)
                    val newSavedEntity = entity.copy(id = localId)

                    // Prepare SyncQueue payload
                    val jsonPayload = JSONObject().apply {
                        put("trip", tripId)
                        put("amount", amount)
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
                return@withContext Result.success(savedEntity)
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                lastException = e
                retries++
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        Result.failure(Exception("Failed to save expense after \$maxRetries attempts", lastException))
    }

    override fun observeExpensesByTrip(tripId: Long): Flow<List<ExpenseEntity>> {
        return expenseDao.observeByTrip(tripId)
    }

    override suspend fun getTotalExpenseForTrip(tripId: Long): Long = withContext(Dispatchers.IO) {
        return@withContext expenseDao.getTotalAmount(tripId) ?: 0L
    }
}
