package com.souigat.mobile.data.repository

import com.souigat.mobile.data.local.dao.CargoTicketDao
import com.souigat.mobile.data.local.dao.PassengerTicketDao
import com.souigat.mobile.data.local.dao.SyncQueueDao
import com.souigat.mobile.data.local.SouigatDatabase
import com.souigat.mobile.data.local.entity.CargoTicketEntity
import com.souigat.mobile.data.local.entity.PassengerTicketEntity
import com.souigat.mobile.data.local.entity.SyncQueueEntity
import androidx.room.withTransaction
import com.souigat.mobile.domain.model.SyncStatus
import com.souigat.mobile.domain.repository.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

class TicketRepositoryImpl @Inject constructor(
    private val db: SouigatDatabase,
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
            val savedEntity = db.withTransaction {
                // Generate ticket number: PT-{TRIP_ID}-{SEQ}
                // Use getCount() to count ALL tickets (even cancelled) to prevent gaps
                val count = passengerDao.getCount(tripId)
                val seq = count + 1
                val ticketNumber = "PT-$tripId-$seq"
                
                // Stable idempotency key generated ONCE per creation request
                val idempotencyKey = UUID.randomUUID().toString()

                val entity = PassengerTicketEntity(
                    serverId = null,
                    tripId = tripId,
                    ticketNumber = ticketNumber,
                    idempotencyKey = idempotencyKey,
                    passengerName = passengerName,
                    price = price,
                    currency = currency,
                    paymentSource = paymentSource,
                    seatNumber = seatNumber,
                    status = "active"
                )

                val localId = passengerDao.upsert(entity)
                val newSavedEntity = entity.copy(id = localId)

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

                val syncItem = SyncQueueEntity(
                    tripId = tripId,
                    itemType = "passenger_ticket",
                    payload = jsonPayload,
                    idempotencyKey = idempotencyKey,
                    status = SyncStatus.PENDING
                )
                syncQueueDao.enqueue(syncItem)
                
                newSavedEntity
            }
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
            val savedEntity = db.withTransaction {
                // Generate ticket number: CT-{TRIP_ID}-{SEQ}
                val count = cargoDao.getCount(tripId)
                val seq = count + 1
                val ticketNumber = "CT-$tripId-$seq"
                
                // Stable idempotency key generated ONCE per creation request
                val idempotencyKey = UUID.randomUUID().toString()

                val entity = CargoTicketEntity(
                    serverId = null,
                    tripId = tripId,
                    ticketNumber = ticketNumber,
                    idempotencyKey = idempotencyKey,
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
                val newSavedEntity = entity.copy(id = localId)

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

                val syncItem = SyncQueueEntity(
                    tripId = tripId,
                    itemType = "cargo_ticket",
                    payload = jsonPayload,
                    idempotencyKey = idempotencyKey,
                    status = SyncStatus.PENDING
                )
                syncQueueDao.enqueue(syncItem)
                
                newSavedEntity
            }
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
}
