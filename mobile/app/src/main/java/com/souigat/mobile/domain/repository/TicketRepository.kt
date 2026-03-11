package com.souigat.mobile.domain.repository

import com.souigat.mobile.data.local.entity.CargoTicketEntity
import com.souigat.mobile.data.local.entity.PassengerTicketEntity
import kotlinx.coroutines.flow.Flow

interface TicketRepository {
    suspend fun createPassengerTicket(
        tripId: Long,
        passengerName: String,
        price: Long, // in centimes
        currency: String,
        paymentSource: String,
        seatNumber: String
    ): Result<PassengerTicketEntity>

    suspend fun createPassengerTicketBatch(
        tripId: Long,
        count: Int,
        price: Long, // in centimes
        currency: String,
        paymentSource: String,
        seatNumber: String
    ): Result<Int>

    suspend fun createCargoTicket(
        tripId: Long,
        senderName: String,
        senderPhone: String,
        receiverName: String,
        receiverPhone: String,
        cargoTier: String,
        description: String,
        price: Long, // in centimes
        currency: String,
        paymentSource: String
    ): Result<CargoTicketEntity>

    fun observePassengerTickets(tripId: Long): Flow<List<PassengerTicketEntity>>
    fun observeCargoTickets(tripId: Long): Flow<List<CargoTicketEntity>>
}
