package com.souigat.mobile.data.local.dao

import androidx.room.*
import com.souigat.mobile.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

data class ExpenseDashboardActivityRow(
    val id: Long,
    val category: String,
    val description: String,
    val amount: Long,
    val currency: String,
    val createdAt: Long,
    val routeLabel: String?
)

data class ExpenseListRow(
    val id: Long,
    val category: String,
    val description: String,
    val amount: Long,
    val currency: String,
    val createdAt: Long
)

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY createdAt DESC")
    fun observeByTrip(tripId: Long): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT
            id,
            category,
            description,
            amount,
            currency,
            createdAt
        FROM expenses
        WHERE tripId = :tripId
        ORDER BY createdAt DESC
        """
    )
    fun observeListByTrip(tripId: Long): Flow<List<ExpenseListRow>>

    @Query(
        "SELECT expenses.* FROM expenses " +
            "INNER JOIN trips ON expenses.tripId = trips.id " +
            "WHERE trips.id = :tripId " +
            "OR trips.serverId = :tripId " +
            "ORDER BY expenses.createdAt DESC"
    )
    fun observeByTripOrServerId(tripId: Long): Flow<List<ExpenseEntity>>

    /** Last 5 expenses for the Dashboard activity feed. */
    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY createdAt DESC LIMIT 5")
    fun observeRecentByTrip(tripId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY createdAt DESC LIMIT 12")
    fun observeRecentGlobal(): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT
            expenses.id AS id,
            expenses.category AS category,
            expenses.description AS description,
            expenses.amount AS amount,
            expenses.currency AS currency,
            expenses.createdAt AS createdAt,
            CASE
                WHEN trips.id IS NULL THEN NULL
                ELSE trips.originOffice || ' -> ' || trips.destinationOffice
            END AS routeLabel
        FROM expenses
        LEFT JOIN trips ON expenses.tripId = trips.id
        ORDER BY expenses.createdAt DESC
        LIMIT 4
        """
    )
    fun observeRecentDashboardItems(): Flow<List<ExpenseDashboardActivityRow>>

    /** Reactive total expenses for the Dashboard stats grid. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE tripId = :tripId AND status = 'active'")
    fun observeTotalAmount(tripId: Long): Flow<Long>

    @Query("SELECT SUM(amount) FROM expenses WHERE tripId = :tripId AND status = 'active'")
    suspend fun getTotalAmount(tripId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun upsert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("UPDATE expenses SET status = 'cancelled' WHERE id = :id")
    suspend fun cancel(id: Long)
}
