package com.souigat.mobile.data.local.dao

import androidx.room.*
import com.souigat.mobile.data.local.entity.CargoTicketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CargoTicketDao {

    @Query("SELECT * FROM cargo_tickets WHERE tripId = :tripId ORDER BY createdAt DESC")
    fun observeByTrip(tripId: Long): Flow<List<CargoTicketEntity>>

    @Query(
        "SELECT * FROM cargo_tickets " +
            "WHERE tripId = :tripId " +
            "OR tripId = COALESCE((SELECT id FROM trips WHERE serverId = :tripId LIMIT 1), -1) " +
            "ORDER BY createdAt DESC"
    )
    fun observeByTripOrServerId(tripId: Long): Flow<List<CargoTicketEntity>>

    @Query("SELECT COUNT(*) FROM cargo_tickets WHERE tripId = :tripId")
    suspend fun getCount(tripId: Long): Int

    @Query("SELECT * FROM cargo_tickets ORDER BY createdAt DESC LIMIT 12")
    fun observeRecentGlobal(): Flow<List<CargoTicketEntity>>

    @Query("SELECT COUNT(*) FROM cargo_tickets WHERE ticketNumber LIKE :datePrefix || '%'")
    suspend fun getCountByDate(datePrefix: String): Int

    @Query(
        "SELECT ticketNumber FROM cargo_tickets " +
            "WHERE ticketNumber LIKE :datePrefix || '-%' " +
            "ORDER BY ticketNumber DESC LIMIT 1"
    )
    suspend fun getLatestTicketNumberByDate(datePrefix: String): String?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun upsert(ticket: CargoTicketEntity): Long

    @Update
    suspend fun update(ticket: CargoTicketEntity)

    @Query("UPDATE cargo_tickets SET status = :newStatus WHERE id = :id")
    suspend fun updateStatus(id: Long, newStatus: String)
}
