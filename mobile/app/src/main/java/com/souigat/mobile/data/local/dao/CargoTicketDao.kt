package com.souigat.mobile.data.local.dao

import androidx.room.*
import com.souigat.mobile.data.local.entity.CargoTicketEntity
import kotlinx.coroutines.flow.Flow

data class CargoDashboardActivityRow(
    val id: Long,
    val ticketNumber: String,
    val senderName: String,
    val receiverName: String,
    val price: Long,
    val currency: String,
    val createdAt: Long,
    val routeLabel: String?
)

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

    @Query(
        """
        SELECT
            cargo_tickets.id AS id,
            cargo_tickets.ticketNumber AS ticketNumber,
            cargo_tickets.senderName AS senderName,
            cargo_tickets.receiverName AS receiverName,
            cargo_tickets.price AS price,
            cargo_tickets.currency AS currency,
            cargo_tickets.createdAt AS createdAt,
            CASE
                WHEN trips.id IS NULL THEN NULL
                ELSE trips.originOffice || ' -> ' || trips.destinationOffice
            END AS routeLabel
        FROM cargo_tickets
        LEFT JOIN trips ON cargo_tickets.tripId = trips.id
        ORDER BY cargo_tickets.createdAt DESC
        LIMIT 4
        """
    )
    fun observeRecentDashboardItems(): Flow<List<CargoDashboardActivityRow>>

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
