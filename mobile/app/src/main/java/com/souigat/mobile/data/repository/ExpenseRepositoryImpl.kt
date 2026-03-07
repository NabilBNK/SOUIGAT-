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
        return@withContext try {
            val idempotencyKey = UUID.randomUUID().toString()
            val savedEntity = db.withTransaction {
                val entity = ExpenseEntity(
                    serverId = null,
                    tripId = tripId,
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
            Result.success(savedEntity)
        } catch (e: Exception) {
            // Expenses do not have sequence collisions like tickets do, 
            // so we do not retry on constraint exceptions here.
            Result.failure(e)
        }
    }

    override fun observeExpensesByTrip(tripId: Long): Flow<List<ExpenseEntity>> {
        return expenseDao.observeByTrip(tripId)
    }

    override suspend fun getTotalExpenseForTrip(tripId: Long): Long = withContext(Dispatchers.IO) {
        return@withContext expenseDao.getTotalAmount(tripId) ?: 0L
    }
}
