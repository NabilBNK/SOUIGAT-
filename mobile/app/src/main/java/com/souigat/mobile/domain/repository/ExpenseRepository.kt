package com.souigat.mobile.domain.repository

import com.souigat.mobile.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    suspend fun createExpense(
        tripId: Long,
        amount: Long,
        currency: String,
        category: String,
        description: String
    ): Result<ExpenseEntity>

    fun observeExpensesByTrip(tripId: Long): Flow<List<ExpenseEntity>>
    
    suspend fun getTotalExpenseForTrip(tripId: Long): Long
}
