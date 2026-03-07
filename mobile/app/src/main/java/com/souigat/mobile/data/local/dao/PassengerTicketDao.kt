package com.souigat.mobile.data.local.dao

import androidx.room.*
import com.souigat.mobile.data.local.entity.PassengerTicketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PassengerTicketDao {

    @Query("SELECT * FROM passenger_tickets WHERE tripId = :tripId ORDER BY createdAt DESC")
    fun observeByTrip(tripId: Long): Flow<List<PassengerTicketEntity>>

    @Query("SELECT COUNT(*) FROM passenger_tickets WHERE tripId = :tripId AND status = 'active'")
    suspend fun getActiveCount(tripId: Long): Int

    @Query("SELECT COUNT(*) FROM passenger_tickets WHERE tripId = :tripId")
    suspend fun getCount(tripId: Long): Int

    @Query("SELECT COUNT(*) FROM passenger_tickets WHERE ticketNumber LIKE :datePrefix || '%'")
    suspend fun getCountByDate(datePrefix: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun upsert(ticket: PassengerTicketEntity): Long

    @Update
    suspend fun update(ticket: PassengerTicketEntity)

    @Query("UPDATE passenger_tickets SET status = 'cancelled' WHERE id = :id")
    suspend fun cancel(id: Long)
}
