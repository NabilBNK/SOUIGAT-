package com.souigat.mobile.data.local.dao

import androidx.room.*
import com.souigat.mobile.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

data class OperationalTripRow(
    val tripId: Long,
    val originOffice: String,
    val destinationOffice: String,
    val busPlate: String,
    val status: String,
    val departureDateTime: Long,
    val passengerBasePrice: Long,
    val currency: String
)

data class HistoryTripRow(
    val tripId: Long,
    val departureDateTime: Long,
    val originOffice: String,
    val destinationOffice: String,
    val busPlate: String,
    val status: String,
    val passengerBasePrice: Long,
    val currency: String
)

@Dao
interface TripDao {

    @Query("SELECT * FROM trips ORDER BY departureDateTime DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY departureDateTime DESC")
    suspend fun getAllNow(): List<TripEntity>

    @Query(
        "SELECT * FROM trips " +
            "WHERE status IN ('scheduled', 'in_progress') " +
            "ORDER BY CASE WHEN status = 'in_progress' THEN 0 ELSE 1 END, departureDateTime ASC"
    )
    fun observeOperationalTrips(): Flow<List<TripEntity>>

    @Query(
        """
        SELECT
            COALESCE(serverId, id) AS tripId,
            originOffice,
            destinationOffice,
            busPlate,
            status,
            departureDateTime,
            passengerBasePrice,
            currency
        FROM trips
        WHERE status IN ('scheduled', 'in_progress')
        ORDER BY CASE WHEN status = 'in_progress' THEN 0 ELSE 1 END, departureDateTime ASC
        """
    )
    fun observeOperationalSummaries(): Flow<List<OperationalTripRow>>

    /** Observe the single active trip (in_progress). Returns null when no active trip. */
    @Query("SELECT * FROM trips WHERE status = 'in_progress' ORDER BY departureDateTime DESC LIMIT 1")
    fun observeActiveTrip(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE status IN (:statuses) ORDER BY departureDateTime DESC")
    fun observeByStatus(statuses: List<String>): Flow<List<TripEntity>>

    @Query(
        """
        SELECT
            COALESCE(serverId, id) AS tripId,
            departureDateTime,
            originOffice,
            destinationOffice,
            busPlate,
            status,
            passengerBasePrice,
            currency
        FROM trips
        WHERE status IN ('completed', 'cancelled')
        ORDER BY departureDateTime DESC
        """
    )
    fun observeHistorySummaries(): Flow<List<HistoryTripRow>>

    @Query("SELECT * FROM trips WHERE id = :id OR serverId = :id LIMIT 1")
    fun observeByLocalOrServerId(id: Long): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :id OR serverId = :id LIMIT 1")
    suspend fun getByLocalOrServerId(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE serverId IN (:serverIds)")
    suspend fun getByServerIds(serverIds: List<Long>): List<TripEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Transaction
    suspend fun upsert(trip: TripEntity): Long {
        val insertedId = insertOrIgnore(trip)
        if (insertedId != -1L) {
            return insertedId
        }

        // Avoid SQLite REPLACE here: deleting the parent trip row cascades into
        // locally created tickets and expenses, making them vanish from conductor history.
        update(trip)
        return trip.id
    }

    @Transaction
    suspend fun upsertAll(trips: List<TripEntity>) {
        for (trip in trips) {
            upsert(trip)
        }
    }

    @Query("DELETE FROM trips WHERE status IN ('scheduled', 'in_progress') AND serverId IS NOT NULL")
    suspend fun clearOperationalServerTrips()

    @Query(
        "DELETE FROM trips " +
            "WHERE status IN ('scheduled', 'in_progress') " +
            "AND serverId IS NOT NULL " +
            "AND serverId NOT IN (:serverIds)"
    )
    suspend fun pruneOperationalServerTrips(serverIds: List<Long>)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteById(id: Long)
}
