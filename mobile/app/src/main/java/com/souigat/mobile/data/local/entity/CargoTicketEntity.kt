package com.souigat.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cargo_tickets",
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
        Index("status"),
        Index("createdAt"),
        Index("ticketNumber", unique = true)
    ]
)
data class CargoTicketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long?,
    val tripId: Long,
    val ticketNumber: String,
    val idempotencyKey: String,
    val senderName: String,
    val senderPhone: String,
    val receiverName: String,
    val receiverPhone: String,
    val cargoTier: String,           // small | medium | large
    val description: String,
    val price: Long,                 // centimes
    val currency: String,
    val paymentSource: String,       // cash | prepaid
    val status: String,              // created | loaded | in_transit | arrived | delivered
    val createdAt: Long = System.currentTimeMillis()
)
