package com.souigat.mobile.data.local.dao

import androidx.room.*
import com.souigat.mobile.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Query("SELECT * FROM trips ORDER BY departureDateTime DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE status IN (:statuses) ORDER BY departureDateTime DESC")
    fun observeByStatus(statuses: List<String>): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: Long): TripEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteById(id: Long)
}
