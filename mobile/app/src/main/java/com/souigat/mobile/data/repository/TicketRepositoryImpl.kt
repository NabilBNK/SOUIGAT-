package com.souigat.mobile.data.repository

import androidx.room.withTransaction
import com.souigat.mobile.data.local.SouigatDatabase
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.local.dao.CargoTicketDao
import com.souigat.mobile.data.local.dao.PassengerTicketDao
import com.souigat.mobile.data.local.dao.SyncQueueDao
import com.souigat.mobile.data.local.entity.CargoTicketEntity
import com.souigat.mobile.data.local.entity.PassengerTicketEntity
import com.souigat.mobile.data.local.entity.SyncQueueEntity
import com.souigat.mobile.domain.model.SyncStatus
import com.souigat.mobile.domain.repository.TicketRepository
import com.souigat.mobile.worker.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class TicketRepositoryImpl @Inject constructor(
    private val db: SouigatDatabase,
    private val passengerDao: PassengerTicketDao,
    private val cargoDao: CargoTicketDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncScheduler: SyncScheduler,
    private val tokenManager: TokenManager
) : TicketRepository {

    override suspend fun createPassengerTicket(
        tripId: Long,
        passengerName: String,
        price: Long,
        currency: String,
        paymentSource: String,
        seatNumber: String,
        boardingPoint: String,
        alightingPoint: String
    ): Result<PassengerTicketEntity> = withContext(Dispatchers.IO) {
        val localTripId = resolveLocalTripId(tripId)
            ?: run {
                Timber.w("createPassengerTicket aborted: trip not found locally. requestedTripId=%d", tripId)
                return@withContext Result.failure(buildMissingTripException(tripId))
            }

        var retries = 0
        val maxRetries = 5
        var lastException: Exception? = null
        val idempotencyKey = UUID.randomUUID().toString()
        val dateString = currentDateStampUtc()
        val ticketPrefix = "PT-$localTripId-$dateString"

        while (retries < maxRetries) {
            try {
                val savedEntity = db.withTransaction {
                    val latestTicketNumber = passengerDao.getLatestTicketNumberByDate(ticketPrefix)
                    val lastSeq = extractTicketSequence(latestTicketNumber, ticketPrefix)
                        ?: passengerDao.getCountByDate(ticketPrefix)
                    val seq = lastSeq + 1 + retries
                    val ticketNumber = "$ticketPrefix-${formatSequence(seq)}"

                    val entity = PassengerTicketEntity(
                        serverId = null,
                        tripId = localTripId,
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
                    val jsonPayload = JSONObject().apply {
                        put("trip", tripId)
                        put("ticket_number", ticketNumber)
                        put("passenger_name", passengerName)
                        put("price", price)
                        put("money_scale", "base_unit")
                        put("currency", currency)
                        put("payment_source", paymentSource)
                        put("seat_number", seatNumber)
                        if (boardingPoint.isNotBlank()) {
                            put("boarding_point", boardingPoint)
                        }
                        if (alightingPoint.isNotBlank()) {
                            put("alighting_point", alightingPoint)
                        }
                    }.toString()

                    syncQueueDao.enqueue(
                        SyncQueueEntity(
                            tripId = tripId,
                            itemType = "passenger_ticket",
                            payload = jsonPayload,
                            idempotencyKey = idempotencyKey,
                            status = SyncStatus.PENDING
                        )
                    )

                    entity.copy(id = localId)
                }

                syncScheduler.triggerOneTimeSync()
                return@withContext Result.success(savedEntity)
            } catch (e: Exception) {
                if (isForeignKeyConstraint(e)) {
                    Timber.w(e, "createPassengerTicket FK violation. requestedTripId=%d localTripId=%d", tripId, localTripId)
                    return@withContext Result.failure(buildMissingTripException(tripId, e))
                }
                if (!isTicketNumberConflict(e)) {
                    Timber.e(e, "createPassengerTicket failed. requestedTripId=%d localTripId=%d", tripId, localTripId)
                    return@withContext Result.failure(e)
                }
                Timber.w(e, "createPassengerTicket ticket-number collision. retry=%d/%d prefix=%s", retries + 1, maxRetries, ticketPrefix)
                lastException = e
                retries++
            }
        }

        Result.failure(
            Exception(
                "Impossible de generer un billet unique apres $maxRetries tentatives",
                lastException
            )
        )
    }

    override suspend fun createPassengerTicketBatch(
        tripId: Long,
        count: Int,
        price: Long,
        currency: String,
        paymentSource: String,
        seatNumber: String,
        boardingPoint: String,
        alightingPoint: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        val localTripId = resolveLocalTripId(tripId)
            ?: run {
                Timber.w("createPassengerTicketBatch aborted: trip not found locally. requestedTripId=%d", tripId)
                return@withContext Result.failure(buildMissingTripException(tripId))
            }

        var retries = 0
        val maxRetries = 5
        var lastException: Exception? = null
        val dateString = currentDateStampUtc()
        val ticketPrefix = "PT-$localTripId-$dateString"

        while (retries < maxRetries) {
            try {
                db.withTransaction {
                    val entities = mutableListOf<PassengerTicketEntity>()
                    val syncItems = mutableListOf<SyncQueueEntity>()

                    val latestTicketNumber = passengerDao.getLatestTicketNumberByDate(ticketPrefix)
                    val lastSeq = extractTicketSequence(latestTicketNumber, ticketPrefix)
                        ?: passengerDao.getCountByDate(ticketPrefix)
                    val startingSeq = lastSeq + 1 + (retries * count)

                    for (i in 1..count) {
                        val seq = startingSeq + (i - 1)
                        val ticketNumber = "$ticketPrefix-${formatSequence(seq)}"
                        val idempotencyKey = UUID.randomUUID().toString()

                        entities += PassengerTicketEntity(
                            serverId = null,
                            tripId = localTripId,
                            ticketNumber = ticketNumber,
                            idempotencyKey = idempotencyKey,
                            passengerName = "Passager",
                            price = price,
                            currency = currency,
                            paymentSource = paymentSource,
                            seatNumber = seatNumber,
                            status = "active"
                        )

                        val jsonPayload = JSONObject().apply {
                            put("trip", tripId)
                            put("ticket_number", ticketNumber)
                            put("passenger_name", "Passager")
                            put("price", price)
                            put("money_scale", "base_unit")
                            put("currency", currency)
                            put("payment_source", paymentSource)
                            put("seat_number", seatNumber)
                            if (boardingPoint.isNotBlank()) {
                                put("boarding_point", boardingPoint)
                            }
                            if (alightingPoint.isNotBlank()) {
                                put("alighting_point", alightingPoint)
                            }
                        }.toString()

                        syncItems += SyncQueueEntity(
                            tripId = tripId,
                            itemType = "passenger_ticket",
                            payload = jsonPayload,
                            idempotencyKey = idempotencyKey,
                            status = SyncStatus.PENDING
                        )
                    }

                    passengerDao.insertBatch(entities)
                    syncQueueDao.enqueueAll(syncItems)
                }

                syncScheduler.triggerOneTimeSync()
                return@withContext Result.success(count)
            } catch (e: Exception) {
                if (isForeignKeyConstraint(e)) {
                    Timber.w(e, "createPassengerTicketBatch FK violation. requestedTripId=%d localTripId=%d", tripId, localTripId)
                    return@withContext Result.failure(buildMissingTripException(tripId, e))
                }
                if (!isTicketNumberConflict(e)) {
                    Timber.e(e, "createPassengerTicketBatch failed. requestedTripId=%d localTripId=%d", tripId, localTripId)
                    return@withContext Result.failure(e)
                }
                Timber.w(e, "createPassengerTicketBatch ticket-number collision. retry=%d/%d prefix=%s", retries + 1, maxRetries, ticketPrefix)
                lastException = e
                retries++
            }
        }

        Result.failure(
            Exception(
                "Impossible de generer les $count billets apres $maxRetries tentatives",
                lastException
            )
        )
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
        val role = tokenManager.getUserRole().orEmpty()
        if (role !in setOf("admin", "office_staff")) {
            return@withContext Result.failure(
                IllegalStateException("Seuls les admins et agents de bureau peuvent creer des colis.")
            )
        }

        val localTripId = resolveLocalTripId(tripId)
            ?: run {
                Timber.w("createCargoTicket aborted: trip not found locally. requestedTripId=%d", tripId)
                return@withContext Result.failure(buildMissingTripException(tripId))
            }

        var retries = 0
        val maxRetries = 5
        var lastException: Exception? = null
        val idempotencyKey = UUID.randomUUID().toString()
        val dateString = currentDateStampUtc()
        val ticketPrefix = "CT-$localTripId-$dateString"

        while (retries < maxRetries) {
            try {
                val savedEntity = db.withTransaction {
                    val latestTicketNumber = cargoDao.getLatestTicketNumberByDate(ticketPrefix)
                    val lastSeq = extractTicketSequence(latestTicketNumber, ticketPrefix)
                        ?: cargoDao.getCountByDate(ticketPrefix)
                    val seq = lastSeq + 1 + retries
                    val ticketNumber = "$ticketPrefix-${formatSequence(seq)}"

                    val entity = CargoTicketEntity(
                        serverId = null,
                        tripId = localTripId,
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

                    syncQueueDao.enqueue(
                        SyncQueueEntity(
                            tripId = tripId,
                            itemType = "cargo_ticket",
                            payload = jsonPayload,
                            idempotencyKey = idempotencyKey,
                            status = SyncStatus.PENDING
                        )
                    )

                    entity.copy(id = localId)
                }

                syncScheduler.triggerOneTimeSync()
                return@withContext Result.success(savedEntity)
            } catch (e: Exception) {
                if (isForeignKeyConstraint(e)) {
                    Timber.w(e, "createCargoTicket FK violation. requestedTripId=%d localTripId=%d", tripId, localTripId)
                    return@withContext Result.failure(buildMissingTripException(tripId, e))
                }
                if (!isTicketNumberConflict(e)) {
                    Timber.e(e, "createCargoTicket failed. requestedTripId=%d localTripId=%d", tripId, localTripId)
                    return@withContext Result.failure(e)
                }
                Timber.w(e, "createCargoTicket ticket-number collision. retry=%d/%d prefix=%s", retries + 1, maxRetries, ticketPrefix)
                lastException = e
                retries++
            }
        }

        Result.failure(
            Exception(
                "Impossible de generer un billet unique apres $maxRetries tentatives",
                lastException
            )
        )
    }

    override fun observePassengerTicketCount(tripId: Long): Flow<Int> {
        return passengerDao.observeCountByTripOrServerId(tripId)
    }

    override fun observePassengerTickets(tripId: Long): Flow<List<PassengerTicketEntity>> {
        return passengerDao.observeByTripOrServerId(tripId)
    }

    override fun observeCargoTickets(tripId: Long): Flow<List<CargoTicketEntity>> {
        return cargoDao.observeByTripOrServerId(tripId)
    }

    private fun extractTicketSequence(ticketNumber: String?, expectedPrefix: String): Int? {
        if (ticketNumber.isNullOrBlank()) {
            return null
        }

        val strictPattern = Regex("^${Regex.escape(expectedPrefix)}-(\\d+)$")
        val strictValue = strictPattern.find(ticketNumber)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        if (strictValue != null) {
            return strictValue
        }

        // Legacy fallback for old ticket formats kept in local DB.
        return ticketNumber.substringAfterLast("-", "").toIntOrNull()
    }

    private fun formatSequence(seq: Int): String {
        return String.format(Locale.US, "%04d", seq)
    }

    private fun currentDateStampUtc(): String {
        val formatter = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date())
    }

    private suspend fun resolveLocalTripId(requestedTripId: Long): Long? {
        val tripDao = db.tripDao()
        val resolved = tripDao.getByLocalOrServerId(requestedTripId)?.id

        Timber.d(
            "resolveLocalTripId(ticket): requestedTripId=%d resolvedLocalTripId=%s",
            requestedTripId,
            resolved?.toString() ?: "null"
        )
        return resolved
    }

    private fun buildMissingTripException(tripId: Long, cause: Throwable? = null): Exception {
        return IllegalStateException(
            "Trajet introuvable localement (id=$tripId). Rafraichissez la liste des trajets puis reessayez.",
            cause
        )
    }

    private fun isForeignKeyConstraint(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message?.lowercase().orEmpty()
            if (
                message.contains("foreign key constraint failed")
                || message.contains("sqlite_constraint_foreignkey")
                || message.contains("code 787")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun isTicketNumberConflict(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message?.lowercase().orEmpty()
            if (
                message.contains("unique")
                && (message.contains("ticketnumber")
                    || message.contains("ticket_number")
                    || message.contains("passenger_tickets.ticketnumber")
                    || message.contains("cargo_tickets.ticketnumber"))
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
