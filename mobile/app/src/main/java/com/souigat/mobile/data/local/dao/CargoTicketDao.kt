package com.souigat.mobile.data.local.dao

import androidx.room.*
import com.souigat.mobile.data.local.entity.CargoTicketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CargoTicketDao {

    @Query("SELECT * FROM cargo_tickets WHERE tripId = :tripId ORDER BY createdAt DESC")
    fun observeByTrip(tripId: Long): Flow<List<CargoTicketEntity>>

    @Query("SELECT COUNT(*) FROM cargo_tickets WHERE tripId = :tripId")
    suspend fun getCount(tripId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun upsert(ticket: CargoTicketEntity): Long

    @Update
    suspend fun update(ticket: CargoTicketEntity)

    @Query("UPDATE cargo_tickets SET status = :newStatus WHERE id = :id")
    suspend fun updateStatus(id: Long, newStatus: String)
}
