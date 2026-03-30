package com.souigat.mobile.data.repository

import com.google.firebase.firestore.FirebaseFirestoreException
import com.souigat.mobile.data.firebase.CargoTicketMirrorDto
import com.souigat.mobile.data.firebase.FirebaseTripDataSource
import com.souigat.mobile.data.firebase.PassengerTicketMirrorDto
import com.souigat.mobile.data.firebase.TripExpenseMirrorDto
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.local.dao.CargoTicketDao
import com.souigat.mobile.data.local.dao.ExpenseDao
import com.souigat.mobile.data.local.dao.PassengerTicketDao
import com.souigat.mobile.data.local.dao.SyncQueueDao
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.entity.CargoTicketEntity
import com.souigat.mobile.data.local.entity.ExpenseEntity
import com.souigat.mobile.data.local.entity.PassengerTicketEntity
import com.souigat.mobile.data.local.entity.SyncQueueEntity
import com.souigat.mobile.data.local.entity.TripEntity
import com.souigat.mobile.domain.model.TripCompletionRecap
import com.souigat.mobile.domain.model.TripDetail
import com.souigat.mobile.domain.model.TripListItem
import com.souigat.mobile.domain.model.TripRouteSegmentTariff
import com.souigat.mobile.domain.model.TripRouteStop
import com.souigat.mobile.domain.model.TripStatusResult
import com.souigat.mobile.domain.model.SyncStatus
import com.souigat.mobile.domain.repository.TripException
import com.souigat.mobile.domain.repository.TripRepository
import com.souigat.mobile.notification.TripReminderScheduler
import com.souigat.mobile.util.Constants
import com.souigat.mobile.util.isOlderThan
import com.souigat.mobile.worker.SyncScheduler
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

@Singleton
class TripRepositoryImpl @Inject constructor(
    private val tripDao: TripDao,
    private val passengerTicketDao: PassengerTicketDao,
    private val cargoTicketDao: CargoTicketDao,
    private val expenseDao: ExpenseDao,
    private val syncQueueDao: SyncQueueDao,
    private val tokenManager: TokenManager,
    private val tripReminderScheduler: TripReminderScheduler,
    private val firebaseTripDataSource: FirebaseTripDataSource,
    private val syncScheduler: SyncScheduler,
) : TripRepository {

    private var tripMirrorListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var passengerMirrorListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var cargoMirrorListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var expenseMirrorListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var realtimeBoundUserId: Int? = null
    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun getTripList(): Result<List<TripListItem>> {
        val localTrips = tripDao.getOperationalTripsNow()
        val latestLocalUpdate = tripDao.getLatestOperationalUpdateAt()
        val shouldFetchRemote = localTrips.isEmpty() ||
            latestLocalUpdate == null ||
            latestLocalUpdate.isOlderThan(Constants.TRIP_LIST_STALE_MS)

        if (!shouldFetchRemote) {
            return Result.success(localTrips.map { it.toTripListItem() })
        }

        return firebaseTripDataSource.fetchTripList()
            .mapCatching { trips ->
                persistTripListLocally(trips)
                trips
            }
            .recoverCatching { error ->
                if (localTrips.isNotEmpty()) {
                    localTrips.map { it.toTripListItem() }
                } else {
                    throw error.toTripException()
                }
            }
    }

    override suspend fun getTripDetail(id: Long): Result<TripDetail> {
        val localTrip = tripDao.getByLocalOrServerId(id)
        return firebaseTripDataSource.fetchTripDetail(id)
            .mapCatching { detail ->
                persistTripDetailLocally(detail)
                detail
            }
            .recoverCatching { error ->
                if (localTrip != null) {
                    localTrip.toTripDetail()
                } else {
                    throw error.toTripException()
                }
            }
    }

    override suspend fun startTrip(id: Long): Result<TripStatusResult> {
        val tripRef = tripDao.getByLocalOrServerId(id)
            ?: return Result.failure(TripException.DataError("Trajet introuvable localement."))

        val targetTripId = tripRef.serverId ?: tripRef.id

        return firebaseTripDataSource.updateTripStatus(targetTripId, "in_progress")
            .fold(
                onSuccess = {
                    updateLocalTripStatus(id, "in_progress")
                    Result.success(TripStatusResult(status = "in_progress"))
                },
                onFailure = { error ->
                    if (isRetryableLifecycleError(error)) {
                        queueTripStatusSync(targetTripId, "in_progress", System.currentTimeMillis())
                        updateLocalTripStatus(id, "in_progress")
                        Result.success(TripStatusResult(status = "in_progress"))
                    } else {
                        Result.failure(error.toTripException())
                    }
                },
            )
    }

    override suspend fun completeTrip(id: Long): Result<TripStatusResult> {
        val tripRef = tripDao.getByLocalOrServerId(id)
            ?: return Result.failure(TripException.DataError("Trajet introuvable localement."))

        val targetTripId = tripRef.serverId ?: tripRef.id

        return firebaseTripDataSource.updateTripStatus(targetTripId, "completed")
            .fold(
                onSuccess = {
                    updateLocalTripStatus(id, "completed")
                    val recap = buildLocalCompletionRecap(id)
                    Result.success(TripStatusResult(status = "completed", completionRecap = recap))
                },
                onFailure = { error ->
                    if (isRetryableLifecycleError(error)) {
                        queueTripStatusSync(targetTripId, "completed", System.currentTimeMillis())
                        updateLocalTripStatus(id, "completed")
                        val recap = buildLocalCompletionRecap(id)
                        Result.success(TripStatusResult(status = "completed", completionRecap = recap))
                    } else {
                        Result.failure(error.toTripException())
                    }
                },
            )
    }

    override suspend fun refreshTripActivity(id: Long): Result<Unit> {
        val passengerResult = firebaseTripDataSource.fetchPassengerTicketsForTrip(id)
        val cargoResult = firebaseTripDataSource.fetchCargoTicketsForTrip(id)
        val expenseResult = firebaseTripDataSource.fetchTripExpensesForTrip(id)

        passengerResult.getOrNull()?.let { tickets ->
            persistPassengerTicketsLocally(tickets)
        }
        cargoResult.getOrNull()?.let { tickets ->
            persistCargoTicketsLocally(tickets)
        }
        expenseResult.getOrNull()?.let { expenses ->
            persistTripExpensesLocally(expenses)
        }

        val firstFailure = listOfNotNull(
            passengerResult.exceptionOrNull(),
            cargoResult.exceptionOrNull(),
            expenseResult.exceptionOrNull(),
        ).firstOrNull()

        if (
            firstFailure != null &&
            passengerResult.getOrNull().isNullOrEmpty() &&
            cargoResult.getOrNull().isNullOrEmpty() &&
            expenseResult.getOrNull().isNullOrEmpty()
        ) {
            return Result.failure(firstFailure.toTripException())
        }

        return Result.success(Unit)
    }

    private suspend fun queueTripStatusSync(tripServerId: Long, status: String, transitionAtMillis: Long) {
        val payload = JSONObject()
            .put("status", status)
            .put("transition_at", transitionAtMillis)
            .toString()

        val queued = syncQueueDao.enqueue(
            SyncQueueEntity(
                tripId = tripServerId,
                itemType = "trip_status",
                idempotencyKey = "trip-status-$tripServerId-$status",
                payload = payload,
                status = SyncStatus.PENDING,
            )
        )

        if (queued > 0L) {
            syncScheduler.triggerOneTimeSync()
        }
    }

    private fun isRetryableLifecycleError(error: Throwable): Boolean {
        if (error is IOException) {
            return true
        }

        if (error is FirebaseFirestoreException) {
            return error.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                error.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ||
                error.code == FirebaseFirestoreException.Code.ABORTED ||
                error.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED
        }

        return false
    }

    override suspend fun startRealtimeTripSync(): Result<Unit> {
        val currentUserId = tokenManager.getUserId()
        if (tripMirrorListener != null && realtimeBoundUserId == currentUserId) {
            return Result.success(Unit)
        }

        if (tripMirrorListener != null && realtimeBoundUserId != currentUserId) {
            stopRealtimeTripSync()
        }

        val tripResult = firebaseTripDataSource.listenTripList(
            onUpdate = { trips ->
                if (trips.isEmpty()) {
                    return@listenTripList
                }

                realtimeScope.launch {
                    persistTripListLocally(trips)
                }
            },
            onError = { error ->
                Timber.w(error, "TripRepositoryImpl: Firestore realtime listener failed.")
            },
        )

        val tripRegistration = tripResult.getOrElse { return Result.failure(it.toTripException()) }

        tripMirrorListener = tripRegistration
        passengerMirrorListener = null
        cargoMirrorListener = null
        expenseMirrorListener = null
        realtimeBoundUserId = currentUserId

        return Result.success(Unit)
    }

    override fun stopRealtimeTripSync() {
        tripMirrorListener?.remove()
        passengerMirrorListener?.remove()
        cargoMirrorListener?.remove()
        expenseMirrorListener?.remove()
        tripMirrorListener = null
        passengerMirrorListener = null
        cargoMirrorListener = null
        expenseMirrorListener = null
        realtimeBoundUserId = null
    }

    private suspend fun persistTripListLocally(trips: List<TripListItem>) {
        val serverIds = trips.map { it.id }
        if (serverIds.isEmpty()) {
            tripDao.clearOperationalServerTrips()
            tripReminderScheduler.syncTrips(emptyList())
            return
        }

        tripDao.pruneOperationalServerTrips(serverIds)

        val existingByServerId = tripDao.getByServerIds(serverIds)
            .associateBy { it.serverId }
        val updatedAt = System.currentTimeMillis()

        val entities = trips.map { dto ->
            val serverTripId = dto.id
            val existing = existingByServerId[serverTripId]

            TripEntity(
                id = existing?.id ?: serverTripId,
                serverId = serverTripId,
                originOffice = dto.origin,
                destinationOffice = dto.destination,
                conductorId = existing?.conductorId ?: 0L,
                busPlate = dto.plate,
                status = dto.status,
                departureDateTime = parseEpochMillis(dto.departureDatetime),
                passengerBasePrice = dto.passengerBasePrice,
                cargoSmallPrice = dto.cargoSmallPrice ?: existing?.cargoSmallPrice ?: 0L,
                cargoMediumPrice = dto.cargoMediumPrice ?: existing?.cargoMediumPrice ?: 0L,
                cargoLargePrice = dto.cargoLargePrice ?: existing?.cargoLargePrice ?: 0L,
                currency = dto.currency,
                routeTemplateName = existing?.routeTemplateName ?: "",
                routeStopSnapshot = existing?.routeStopSnapshot ?: "[]",
                routeSegmentTariffSnapshot = existing?.routeSegmentTariffSnapshot ?: "[]",
                updatedAt = updatedAt,
            )
        }

        tripDao.upsertAll(entities)
        tripReminderScheduler.syncTrips(entities)
    }

    private suspend fun persistTripDetailLocally(detail: TripDetail) {
        val serverTripId = detail.id
        val existing = tripDao.getByLocalOrServerId(serverTripId)

        val entity = TripEntity(
            id = existing?.id ?: serverTripId,
            serverId = serverTripId,
            originOffice = detail.originName,
            destinationOffice = detail.destinationName,
            conductorId = detail.conductorId.toLong(),
            busPlate = detail.busPlate,
            status = detail.status,
            departureDateTime = parseEpochMillis(detail.departureDatetime),
            passengerBasePrice = detail.passengerBasePrice,
            cargoSmallPrice = detail.cargoSmallPrice,
            cargoMediumPrice = detail.cargoMediumPrice,
            cargoLargePrice = detail.cargoLargePrice,
            currency = detail.currency,
            routeTemplateName = detail.routeTemplateName,
            routeStopSnapshot = encodeRouteStops(detail.routeStops),
            routeSegmentTariffSnapshot = encodeRouteSegmentTariffs(detail.routeSegmentTariffs),
            updatedAt = System.currentTimeMillis(),
        )
        tripDao.upsert(entity)
        tripReminderScheduler.syncTrip(entity)
    }

    private suspend fun updateLocalTripStatus(serverTripId: Long, newStatus: String) {
        val existing = tripDao.getByLocalOrServerId(serverTripId) ?: return
        val updatedTrip = existing.copy(status = newStatus, updatedAt = System.currentTimeMillis())
        tripDao.update(updatedTrip)
        tripReminderScheduler.syncTrip(updatedTrip)
    }

    private suspend fun buildLocalCompletionRecap(tripRefId: Long): TripCompletionRecap {
        val localTrip = tripDao.getByLocalOrServerId(tripRefId)
            ?: throw TripException.DataError("Trajet introuvable pour le recapitulatif.")

        val tripId = localTrip.id
        val passengerCashTotal = passengerTicketDao.getCashTotalByTrip(tripId)
        val cargoCashTotal = cargoTicketDao.getCashTotalByTrip(tripId)
        val expensesTotal = expenseDao.getTotalAmount(tripId) ?: 0L
        val passengerCount = passengerTicketDao.getActiveCount(tripId)
        val cargoCount = cargoTicketDao.getActiveCount(tripId)

        return TripCompletionRecap(
            passengerCashTotal = passengerCashTotal,
            cargoCashTotal = cargoCashTotal,
            expensesTotal = expensesTotal,
            cashExpected = passengerCashTotal + cargoCashTotal - expensesTotal,
            passengerCount = passengerCount,
            cargoCount = cargoCount,
            currency = localTrip.currency,
        )
    }

    private suspend fun persistPassengerTicketsLocally(tickets: List<PassengerTicketMirrorDto>) {
        tickets.forEach { dto ->
            val localTrip = tripDao.getByLocalOrServerId(dto.tripId)
            if (localTrip == null) {
                return@forEach
            }

            val existing = passengerTicketDao.getByServerId(dto.id)
                ?: passengerTicketDao.getByTicketNumber(dto.ticketNumber)

            val next = PassengerTicketEntity(
                id = existing?.id ?: 0,
                serverId = dto.id,
                tripId = localTrip.id,
                ticketNumber = dto.ticketNumber,
                idempotencyKey = existing?.idempotencyKey ?: "mirror-passenger-${dto.id}",
                passengerName = dto.passengerName,
                price = dto.price,
                currency = dto.currency,
                paymentSource = dto.paymentSource,
                seatNumber = dto.seatNumber,
                boardingPoint = dto.boardingPoint,
                alightingPoint = dto.alightingPoint,
                status = dto.status,
                createdAt = parseEpochMillis(dto.createdAtIso),
            )

            if (existing == null) {
                runCatching { passengerTicketDao.upsert(next) }
                    .onFailure { error ->
                        Timber.w(error, "TripRepositoryImpl: failed to insert mirrored passenger ticket id=%d", dto.id)
                    }
            } else {
                runCatching { passengerTicketDao.update(next) }
                    .onFailure { error ->
                        Timber.w(error, "TripRepositoryImpl: failed to update mirrored passenger ticket id=%d", dto.id)
                    }
            }
        }
    }

    private suspend fun persistCargoTicketsLocally(tickets: List<CargoTicketMirrorDto>) {
        tickets.forEach { dto ->
            val localTrip = tripDao.getByLocalOrServerId(dto.tripId)
            if (localTrip == null) {
                return@forEach
            }

            val existing = cargoTicketDao.getByServerId(dto.id)
                ?: cargoTicketDao.getByTicketNumber(dto.ticketNumber)

            val next = CargoTicketEntity(
                id = existing?.id ?: 0,
                serverId = dto.id,
                tripId = localTrip.id,
                ticketNumber = dto.ticketNumber,
                idempotencyKey = existing?.idempotencyKey ?: "mirror-cargo-${dto.id}",
                senderName = dto.senderName,
                senderPhone = dto.senderPhone,
                receiverName = dto.receiverName,
                receiverPhone = dto.receiverPhone,
                cargoTier = dto.cargoTier,
                description = dto.description,
                price = dto.price,
                currency = dto.currency,
                paymentSource = dto.paymentSource,
                status = dto.status,
                createdAt = parseEpochMillis(dto.createdAtIso),
            )

            if (existing == null) {
                runCatching { cargoTicketDao.upsert(next) }
                    .onFailure { error ->
                        Timber.w(error, "TripRepositoryImpl: failed to insert mirrored cargo ticket id=%d", dto.id)
                    }
            } else {
                runCatching { cargoTicketDao.update(next) }
                    .onFailure { error ->
                        Timber.w(error, "TripRepositoryImpl: failed to update mirrored cargo ticket id=%d", dto.id)
                    }
            }
        }
    }

    private suspend fun persistTripExpensesLocally(expenses: List<TripExpenseMirrorDto>) {
        expenses.forEach { dto ->
            val localTrip = tripDao.getByLocalOrServerId(dto.tripId)
            if (localTrip == null) {
                return@forEach
            }

            val existing = expenseDao.getByServerId(dto.id)
            val next = ExpenseEntity(
                id = existing?.id ?: 0,
                serverId = dto.id,
                tripId = localTrip.id,
                idempotencyKey = existing?.idempotencyKey ?: "mirror-expense-${dto.id}",
                amount = dto.amount,
                currency = dto.currency,
                category = dto.category,
                description = dto.description,
                status = existing?.status ?: "active",
                createdAt = parseEpochMillis(dto.createdAtIso),
            )

            if (existing == null) {
                runCatching { expenseDao.upsert(next) }
                    .onFailure { error ->
                        Timber.w(error, "TripRepositoryImpl: failed to insert mirrored expense id=%d", dto.id)
                    }
            } else {
                runCatching { expenseDao.update(next) }
                    .onFailure { error ->
                        Timber.w(error, "TripRepositoryImpl: failed to update mirrored expense id=%d", dto.id)
                    }
            }
        }
    }

    private fun Throwable.toTripException(): TripException {
        return when (this) {
            is TripException -> this
            is IOException -> TripException.NetworkUnavailable
            is FirebaseFirestoreException -> {
                when (code) {
                    FirebaseFirestoreException.Code.UNAUTHENTICATED -> TripException.Unauthenticated
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> TripException.NotAssigned
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                    FirebaseFirestoreException.Code.ABORTED,
                    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> TripException.NetworkUnavailable
                    FirebaseFirestoreException.Code.INVALID_ARGUMENT,
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION -> TripException.InvalidStatus(message ?: "Transition invalide")
                    else -> TripException.DataError(message ?: "Erreur Firebase")
                }
            }
            else -> TripException.DataError(message ?: "Erreur inconnue")
        }
    }

    private fun parseEpochMillis(raw: String): Long {
        return try {
            java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.LocalDateTime.parse(raw)
                    .toInstant(java.time.ZoneOffset.UTC)
                    .toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    private fun TripEntity.toTripListItem(): TripListItem {
        return TripListItem(
            id = serverId ?: id,
            origin = originOffice,
            destination = destinationOffice,
            conductorName = "",
            plate = busPlate,
            departureDatetime = departureDateTime.toIsoOffsetDateTime(),
            status = status,
            passengerBasePrice = passengerBasePrice,
            cargoSmallPrice = cargoSmallPrice,
            cargoMediumPrice = cargoMediumPrice,
            cargoLargePrice = cargoLargePrice,
            currency = currency,
        )
    }

    private fun TripEntity.toTripDetail(): TripDetail {
        return TripDetail(
            id = serverId ?: id,
            originOfficeId = 0,
            destinationOfficeId = 0,
            conductorId = conductorId.toInt(),
            busId = 0,
            departureDatetime = departureDateTime.toIsoOffsetDateTime(),
            arrivalDatetime = null,
            status = status,
            passengerBasePrice = passengerBasePrice,
            cargoSmallPrice = cargoSmallPrice,
            cargoMediumPrice = cargoMediumPrice,
            cargoLargePrice = cargoLargePrice,
            currency = currency,
            conductorName = "",
            busPlate = busPlate,
            originName = originOffice,
            destinationName = destinationOffice,
            routeTemplateName = routeTemplateName,
            routeStops = decodeRouteStops(routeStopSnapshot),
            routeSegmentTariffs = decodeRouteSegmentTariffs(routeSegmentTariffSnapshot),
        )
    }

    private fun Long.toIsoOffsetDateTime(): String {
        return Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).toString()
    }

    private fun encodeRouteStops(stops: List<TripRouteStop>): String {
        val array = JSONArray()
        stops.sortedBy { it.stopOrder }.forEach { stop ->
            val label = stop.officeName.trim()
            array.put(
                JSONObject()
                    .put("office_id", stop.officeId)
                    .put("office_name", label)
                    .put("stop_name", label)
                    .put("stop_order", stop.stopOrder)
            )
        }
        return array.toString()
    }

    private fun encodeRouteSegmentTariffs(segments: List<TripRouteSegmentTariff>): String {
        val array = JSONArray()
        segments.sortedBy { it.fromStopOrder }.forEach { segment ->
            array.put(
                JSONObject()
                    .put("from_stop_order", segment.fromStopOrder)
                    .put("to_stop_order", segment.toStopOrder)
                    .put("passenger_price", segment.passengerPrice)
                    .put("currency", segment.currency)
            )
        }
        return array.toString()
    }

    private fun decodeRouteStops(raw: String): List<TripRouteStop> {
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val stopName = item.optString("stop_name", "")
                    val officeName = item.optString("office_name", "")
                    val displayName = stopName.takeIf { it.isNotBlank() } ?: officeName
                    val officeId = item.optInt("office_id", -1)
                    val stopOrder = item.optInt("stop_order", -1)
                    if (displayName.isBlank() || stopOrder < 0) {
                        continue
                    }
                    add(
                        TripRouteStop(
                            officeId = officeId,
                            officeName = displayName,
                            stopOrder = stopOrder,
                        )
                    )
                }
            }.sortedBy { it.stopOrder }
        }.getOrElse { emptyList() }
    }

    private fun decodeRouteSegmentTariffs(raw: String): List<TripRouteSegmentTariff> {
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val fromStopOrder = item.optInt("from_stop_order", -1)
                    val toStopOrder = item.optInt("to_stop_order", -1)
                    val passengerPrice = item.optLong("passenger_price", -1L)
                    val currency = item.optString("currency", "DZD")
                    if (fromStopOrder < 0 || toStopOrder < 0 || passengerPrice < 0) {
                        continue
                    }
                    add(
                        TripRouteSegmentTariff(
                            fromStopOrder = fromStopOrder,
                            toStopOrder = toStopOrder,
                            passengerPrice = passengerPrice,
                            currency = currency,
                        )
                    )
                }
            }.sortedBy { it.fromStopOrder }
        }.getOrElse { emptyList() }
    }
}

