package com.souigat.mobile.data.local.dao

import androidx.room.*
import com.souigat.mobile.data.local.entity.PassengerTicketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PassengerTicketDao {

    @Query("SELECT * FROM passenger_tickets WHERE tripId = :tripId ORDER BY createdAt DESC")
    fun observeByTrip(tripId: Long): Flow<List<PassengerTicketEntity>>

    /** Last 10 tickets for the activity feed on Dashboard. */
    @Query("SELECT * FROM passenger_tickets WHERE tripId = :tripId ORDER BY createdAt DESC LIMIT 10")
    fun observeRecentByTrip(tripId: Long): Flow<List<PassengerTicketEntity>>

    /** Reactive total revenue (sum of prices in centimes) for a trip. */
    @Query("SELECT COALESCE(SUM(price), 0) FROM passenger_tickets WHERE tripId = :tripId AND status = 'active'")
    fun observeTotalRevenue(tripId: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM passenger_tickets WHERE tripId = :tripId AND status = 'active'")
    suspend fun getActiveCount(tripId: Long): Int

    @Query("SELECT COUNT(*) FROM passenger_tickets WHERE tripId = :tripId")
    suspend fun getCount(tripId: Long): Int

    @Query("SELECT COUNT(*) FROM passenger_tickets WHERE ticketNumber LIKE :datePrefix || '%'")
    suspend fun getCountByDate(datePrefix: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun upsert(ticket: PassengerTicketEntity): Long

    @Transaction
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(tickets: List<PassengerTicketEntity>)

    @Update
    suspend fun update(ticket: PassengerTicketEntity)

    @Query("UPDATE passenger_tickets SET status = 'cancelled' WHERE id = :id")
    suspend fun cancel(id: Long)
}
