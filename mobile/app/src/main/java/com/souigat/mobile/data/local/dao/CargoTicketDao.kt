package com.souigat.mobile.data.local.dao

import androidx.room.*
import com.souigat.mobile.data.local.entity.CargoTicketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CargoTicketDao {

    @Query("SELECT * FROM cargo_tickets WHERE tripId = :tripId ORDER BY createdAt DESC")
    fun observeByTrip(tripId: Long): Flow<List<CargoTicketEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ticket: CargoTicketEntity): Long

    @Update
    suspend fun update(ticket: CargoTicketEntity)

    @Query("UPDATE cargo_tickets SET status = :newStatus WHERE id = :id")
    suspend fun updateStatus(id: Long, newStatus: String)
}
