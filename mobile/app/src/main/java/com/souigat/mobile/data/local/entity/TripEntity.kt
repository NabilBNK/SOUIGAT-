package com.souigat.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips",
    indices = [Index("status"), Index("serverId", unique = true)]
)
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long?,             // null until synced
    val originOffice: String,
    val destinationOffice: String,
    val conductorId: Long,
    val busPlate: String,
    val status: String,              // scheduled | in_progress | completed | cancelled
    val departureDateTime: Long,     // epoch millis
    val passengerBasePrice: Long,    // in centimes to avoid float precision issues
    val cargoSmallPrice: Long,
    val cargoMediumPrice: Long,
    val cargoLargePrice: Long,
    val currency: String,
    val updatedAt: Long = System.currentTimeMillis()
)
