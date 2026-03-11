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

@Singleton
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
        var retries = 0
        val maxRetries = 5
        var lastException: Exception? = null

        // Stable idempotency key generated ONCE per creation request
        val idempotencyKey = UUID.randomUUID().toString()

        // Date initialized safely outside loop in UTC for stability across attempts
        val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val dateString = sdf.format(java.util.Date())

        while (retries < maxRetries) {
            try {
                val savedEntity = db.withTransaction {
                    // Generate ticket number: PT-YYYYMMDD-0001
                    // Offset sequence by retries to safely bypass collisions
                    val count = passengerDao.getCountByDate("PT-$dateString")
                    val seq = count + 1 + retries
                    val seqString = String.format(java.util.Locale.US, "%04d", seq)
                    val ticketNumber = "PT-$dateString-$seqString"

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
                return@withContext Result.success(savedEntity)
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                lastException = e
                retries++
                // Loop continues with updated retry count to bypass sequence collision
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        Result.failure(Exception("Impossible de générer un billet unique après $maxRetries tentatives", lastException))
    }

    override suspend fun createPassengerTicketBatch(
        tripId: Long,
        count: Int,
        price: Long,
        currency: String,
        paymentSource: String,
        seatNumber: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        var retries = 0
        val maxRetries = 5
        var lastException: Exception? = null

        val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val dateString = sdf.format(java.util.Date())

        while (retries < maxRetries) {
            try {
                db.withTransaction {
                    val entities = mutableListOf<PassengerTicketEntity>()
                    val syncItems = mutableListOf<SyncQueueEntity>()
                    
                    val currentCount = passengerDao.getCountByDate("PT-$dateString")
                    
                    for (i in 1..count) {
                        val seq = currentCount + i + (retries * count)
                        val seqString = String.format(java.util.Locale.US, "%04d", seq)
                        val ticketNumber = "PT-$dateString-$seqString"
                        val idempotencyKey = UUID.randomUUID().toString()

                        val entity = PassengerTicketEntity(
                            serverId = null,
                            tripId = tripId,
                            ticketNumber = ticketNumber,
                            idempotencyKey = idempotencyKey,
                            passengerName = "Passager",
                            price = price,
                            currency = currency,
                            paymentSource = paymentSource,
                            seatNumber = seatNumber,
                            status = "active"
                        )
                        entities.add(entity)

                        val jsonPayload = JSONObject().apply {
                            put("trip", tripId)
                            put("ticket_number", ticketNumber)
                            put("passenger_name", "Passager")
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
                        syncItems.add(syncItem)
                    }

                    passengerDao.insertBatch(entities)
                    for (item in syncItems) {
                        syncQueueDao.enqueue(item)
                    }
                }
                return@withContext Result.success(count)
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                lastException = e
                retries++
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        Result.failure(Exception("Impossible de générer les $count billets après $maxRetries tentatives", lastException))
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
        var retries = 0
        val maxRetries = 5
        var lastException: Exception? = null

        // Stable idempotency key generated ONCE per creation request
        val idempotencyKey = UUID.randomUUID().toString()

        // Date initialized safely outside loop in UTC for stability across attempts
        val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val dateString = sdf.format(java.util.Date())

        while (retries < maxRetries) {
            try {
                val savedEntity = db.withTransaction {
                    // Generate ticket number: CT-YYYYMMDD-0001
                    val count = cargoDao.getCountByDate("CT-$dateString")
                    val seq = count + 1 + retries
                    val seqString = String.format(java.util.Locale.US, "%04d", seq)
                    val ticketNumber = "CT-$dateString-$seqString"

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
                return@withContext Result.success(savedEntity)
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                lastException = e
                retries++
                // Loop continues with updated retry count to bypass sequence collision
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        Result.failure(Exception("Impossible de générer un billet unique après $maxRetries tentatives", lastException))
    }

    override fun observePassengerTickets(tripId: Long): Flow<List<PassengerTicketEntity>> {
        return passengerDao.observeByTrip(tripId)
    }

    override fun observeCargoTickets(tripId: Long): Flow<List<CargoTicketEntity>> {
        return cargoDao.observeByTrip(tripId)
    }
}
