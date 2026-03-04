package com.souigat.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long?,
    val tripId: Long,
    val amount: Long,                // centimes
    val currency: String,
    val category: String,           // fuel | food | tolls | other
    val description: String,
    val createdAt: Long = System.currentTimeMillis()
)
