package com.souigat.mobile.data.local.dao

import androidx.room.*
import com.souigat.mobile.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY createdAt DESC")
    fun observeByTrip(tripId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT SUM(amount) FROM expenses WHERE tripId = :tripId AND status = 'active'")
    suspend fun getTotalAmount(tripId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun upsert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("UPDATE expenses SET status = 'cancelled' WHERE id = :id")
    suspend fun cancel(id: Long)
}
