package com.souigat.mobile.data.repository

import com.souigat.mobile.data.local.dao.CargoTicketDao
import com.souigat.mobile.data.local.dao.PassengerTicketDao
import com.souigat.mobile.data.local.dao.SyncQueueDao
import com.souigat.mobile.data.local.entity.CargoTicketEntity
import com.souigat.mobile.data.local.entity.PassengerTicketEntity
import com.souigat.mobile.data.local.entity.SyncQueueEntity
import com.souigat.mobile.domain.model.SyncStatus
import com.souigat.mobile.domain.repository.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketRepositoryImpl @Inject constructor(
    private val passengerDao: PassengerTicketDao,
    private val cargoDao: CargoTicketDao,
    private val syncQueueDao: SyncQueueDao
) : TicketRepository {

    override suspend fun createPassengerTicket(
        tripId: Long,
        passengerName: String,
        price: Long,
        currency: String,
        paymentSource: String,
        seatNumber: String
    ): Result<PassengerTicketEntity> = withContext(Dispatchers.IO) {
        try {
            // Generate ticket number: PT-{TRIP_ID}-{SEQ}
            val count = passengerDao.getActiveCount(tripId)
            val seq = count + 1
            val ticketNumber = "PT-$tripId-$seq"

            val entity = PassengerTicketEntity(
                serverId = null,
                tripId = tripId,
                ticketNumber = ticketNumber,
                passengerName = passengerName,
                price = price,
                currency = currency,
                paymentSource = paymentSource,
                seatNumber = seatNumber,
                status = "active"
            )

            val localId = passengerDao.upsert(entity)
            val savedEntity = entity.copy(id = localId)

            // Prepare SyncQueue payload
            val jsonPayload = JSONObject().apply {
                put("trip", tripId)
                put("ticket_number", ticketNumber)
                put("passenger_name", passengerName)
                put("price", price)
                put("currency", currency)
                put("payment_source", paymentSource)
                put("seat_number", seatNumber)
            }.toString()

            val idempotencyKey = hashString(jsonPayload)

            val syncItem = SyncQueueEntity(
                tripId = tripId,
                itemType = "passenger_ticket",
                payload = jsonPayload,
                idempotencyKey = idempotencyKey,
                status = SyncStatus.PENDING
            )
            syncQueueDao.enqueue(syncItem)

            Result.success(savedEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createCargoTicket(
        tripId: Long,
        senderName: String,
        senderPhone: String,
        receiverName: String,
        receiverPhone: String,
        cargoTier: String,
        description: String,
        price: Long,
        currency: String,
        paymentSource: String
    ): Result<CargoTicketEntity> = withContext(Dispatchers.IO) {
        try {
            // Generate ticket number: CT-{TRIP_ID}-{SEQ}
            val count = cargoDao.getCount(tripId)
            val seq = count + 1
            val ticketNumber = "CT-$tripId-$seq"

            val entity = CargoTicketEntity(
                serverId = null,
                tripId = tripId,
                ticketNumber = ticketNumber,
                senderName = senderName,
                senderPhone = senderPhone,
                receiverName = receiverName,
                receiverPhone = receiverPhone,
                cargoTier = cargoTier,
                description = description,
                price = price,
                currency = currency,
                paymentSource = paymentSource,
                status = "created"
            )

            val localId = cargoDao.upsert(entity)
            val savedEntity = entity.copy(id = localId)

            // Prepare SyncQueue payload
            val jsonPayload = JSONObject().apply {
                put("trip", tripId)
                put("ticket_number", ticketNumber)
                put("sender_name", senderName)
                put("sender_phone", senderPhone)
                put("receiver_name", receiverName)
                put("receiver_phone", receiverPhone)
                put("cargo_tier", cargoTier)
                put("description", description)
                put("price", price)
                put("currency", currency)
                put("payment_source", paymentSource)
            }.toString()

            val idempotencyKey = hashString(jsonPayload)

            val syncItem = SyncQueueEntity(
                tripId = tripId,
                itemType = "cargo_ticket",
                payload = jsonPayload,
                idempotencyKey = idempotencyKey,
                status = SyncStatus.PENDING
            )
            syncQueueDao.enqueue(syncItem)

            Result.success(savedEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observePassengerTickets(tripId: Long): Flow<List<PassengerTicketEntity>> {
        return passengerDao.observeByTrip(tripId)
    }

    override fun observeCargoTickets(tripId: Long): Flow<List<CargoTicketEntity>> {
        return cargoDao.observeByTrip(tripId)
    }

    private fun hashString(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
