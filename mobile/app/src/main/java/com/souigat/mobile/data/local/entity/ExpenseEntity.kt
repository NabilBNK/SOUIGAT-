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
    indices = [
        Index("tripId"),
        Index("createdAt"),
        Index(value = ["idempotencyKey"], unique = true)
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long?,
    val tripId: Long,
    val idempotencyKey: String,
    val amount: Long,
    val currency: String,
    val category: String,           // fuel | food | tolls | other
    val description: String,
    val status: String = "active",
    val createdAt: Long = System.currentTimeMillis()
)
