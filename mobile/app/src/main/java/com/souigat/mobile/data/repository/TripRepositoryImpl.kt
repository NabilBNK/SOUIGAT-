package com.souigat.mobile.data.repository

import com.souigat.mobile.data.firebase.CargoTicketMirrorDto
import com.souigat.mobile.data.firebase.PassengerTicketMirrorDto
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.dao.CargoTicketDao
import com.souigat.mobile.data.local.dao.PassengerTicketDao
import com.souigat.mobile.data.local.entity.CargoTicketEntity
import com.souigat.mobile.data.local.entity.PassengerTicketEntity
import com.souigat.mobile.data.local.entity.TripEntity
import com.souigat.mobile.data.firebase.FirebaseTripDataSource
import com.google.firebase.firestore.ListenerRegistration
import com.souigat.mobile.notification.TripReminderScheduler
import com.souigat.mobile.data.remote.api.TripApi
import com.souigat.mobile.data.remote.dto.TripDetailDto
import com.souigat.mobile.data.remote.dto.TripListDto
import com.souigat.mobile.data.remote.dto.TripStatusDto
import com.souigat.mobile.domain.repository.TripException
import com.souigat.mobile.domain.repository.TripRepository
import com.souigat.mobile.util.Constants
import com.souigat.mobile.util.isOlderThan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import org.json.JSONObject
import retrofit2.Response
import timber.log.Timber
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepositoryImpl @Inject constructor(
    private val tripApi: TripApi,
    private val tripDao: TripDao,
    private val passengerTicketDao: PassengerTicketDao,
    private val cargoTicketDao: CargoTicketDao,
    private val tripReminderScheduler: TripReminderScheduler,
    private val firebaseTripDataSource: FirebaseTripDataSource
) : TripRepository {

    private var tripMirrorListener: ListenerRegistration? = null
    private var passengerMirrorListener: ListenerRegistration? = null
    private var cargoMirrorListener: ListenerRegistration? = null
    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun getTripList(): Result<List<TripListDto>> {
        val localTrips = tripDao.getOperationalTripsNow()
        val latestLocalUpdate = tripDao.getLatestOperationalUpdateAt()
        val shouldFetchRemote = localTrips.isEmpty() || latestLocalUpdate == null || latestLocalUpdate.isOlderThan(Constants.TRIP_LIST_STALE_MS)

        if (!shouldFetchRemote) {
            Timber.i("TripRepositoryImpl: using fresh local trips, skipping remote fetch.")
            return Result.success(localTrips.map { it.toTripListDto() })
        }

        val firebaseResult = firebaseTripDataSource.fetchTripList()
        if (firebaseResult.isSuccess) {
            val mirroredTrips = firebaseResult.getOrNull().orEmpty()
            if (mirroredTrips.isNotEmpty()) {
                persistTripListLocally(mirroredTrips)
                return Result.success(mirroredTrips)
            }

            Timber.i("TripRepositoryImpl: Firestore returned no trips, fallback to backend API.")
        } else {
            Timber.w(
                firebaseResult.exceptionOrNull(),
                "TripRepositoryImpl: Firestore list read failed, fallback to backend API.",
            )
        }

        val backendResult = safeApiCall { tripApi.getTripList() }
        return backendResult
            .mapCatching { page ->
                persistTripListLocally(page.results)
                page.results
            }
            .recoverCatching { error ->
                if (localTrips.isNotEmpty()) {
                    Timber.w(error, "TripRepositoryImpl: remote list fetch failed, serving local cached trips.")
                    localTrips.map { it.toTripListDto() }
                } else {
                    throw error
                }
            }
    }

    override suspend fun getTripDetail(id: Long): Result<TripDetailDto> {
        val localTrip = tripDao.getByLocalOrServerId(id)
        if (localTrip != null && !localTrip.updatedAt.isOlderThan(Constants.TRIP_LIST_STALE_MS)) {
            Timber.i("TripRepositoryImpl: using fresh local trip detail for id=%d.", id)
            return Result.success(localTrip.toTripDetailDto())
        }

        val firebaseResult = firebaseTripDataSource.fetchTripDetail(id)
        if (firebaseResult.isSuccess) {
            val mirroredDetail = firebaseResult.getOrNull()
            if (mirroredDetail != null) {
                persistTripDetailLocally(mirroredDetail)
                return Result.success(mirroredDetail)
            }
        } else {
            Timber.w(
                firebaseResult.exceptionOrNull(),
                "TripRepositoryImpl: Firestore detail read failed for trip=%s, fallback to backend API.",
                id,
            )
        }

        return safeApiCall { tripApi.getTripDetail(id) }
            .mapCatching { detail ->
                persistTripDetailLocally(detail)
                detail
            }
            .recoverCatching { error ->
                if (localTrip != null) {
                    Timber.w(error, "TripRepositoryImpl: remote detail fetch failed, serving local cached trip id=%d.", id)
                    localTrip.toTripDetailDto()
                } else {
                    throw error
                }
            }
    }

    override suspend fun startTrip(id: Long): Result<TripStatusDto> {
        return safeApiCall { tripApi.startTrip(id) }
            .mapCatching { status ->
                updateLocalTripStatus(id, status.status)
                status
            }
            .recoverCatching { error ->
                Timber.w(error, "TripRepositoryImpl: backend startTrip failed, trying Firestore fallback.")
                val tripRef = tripDao.getByLocalOrServerId(id)
                val targetTripId = tripRef?.serverId ?: id

                firebaseTripDataSource.updateTripStatus(targetTripId, "in_progress")
                    .getOrElse { throw error }

                updateLocalTripStatus(id, "in_progress")
                TripStatusDto(status = "in_progress")
            }
    }

    override suspend fun completeTrip(id: Long): Result<TripStatusDto> {
        return safeApiCall { tripApi.completeTrip(id) }
            .mapCatching { status ->
                updateLocalTripStatus(id, status.status)
                status
            }
            .recoverCatching { error ->
                Timber.w(error, "TripRepositoryImpl: backend completeTrip failed, trying Firestore fallback.")
                val tripRef = tripDao.getByLocalOrServerId(id)
                val targetTripId = tripRef?.serverId ?: id

                firebaseTripDataSource.updateTripStatus(targetTripId, "completed")
                    .getOrElse { throw error }

                updateLocalTripStatus(id, "completed")
                TripStatusDto(status = "completed")
            }
    }

    override suspend fun startRealtimeTripSync(): Result<Unit> {
        if (tripMirrorListener != null) {
            return Result.success(Unit)
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

        val passengerResult = firebaseTripDataSource.listenPassengerTickets(
            onUpdate = { tickets ->
                realtimeScope.launch {
                    persistPassengerTicketsLocally(tickets)
                }
            },
            onError = { error ->
                Timber.w(error, "TripRepositoryImpl: Firestore realtime passenger listener failed.")
            },
        )

        val cargoResult = firebaseTripDataSource.listenCargoTickets(
            onUpdate = { tickets ->
                realtimeScope.launch {
                    persistCargoTicketsLocally(tickets)
                }
            },
            onError = { error ->
                Timber.w(error, "TripRepositoryImpl: Firestore realtime cargo listener failed.")
            },
        )

        val tripRegistration = tripResult.getOrElse { return Result.failure(it) }
        val passengerRegistration = passengerResult.getOrNull()
        if (passengerRegistration == null) {
            Timber.w(
                passengerResult.exceptionOrNull(),
                "TripRepositoryImpl: passenger realtime listener unavailable, continuing with trips listener.",
            )
        }

        val cargoRegistration = cargoResult.getOrNull()
        if (cargoRegistration == null) {
            Timber.w(
                cargoResult.exceptionOrNull(),
                "TripRepositoryImpl: cargo realtime listener unavailable, continuing with trips listener.",
            )
        }

        tripMirrorListener = tripRegistration
        passengerMirrorListener = passengerRegistration
        cargoMirrorListener = cargoRegistration

        Timber.i("TripRepositoryImpl: Firestore realtime listeners started (trip listener is mandatory, others optional).")
        return Result.success(Unit)
    }

    private suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): Result<T> {
        return try {
            val response = apiCall()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Timber.e("safeApiCall: successful response but null body. code=${response.code()}")
                    Result.failure(TripException.ServerError(response.code()))
                }
            } else {
                val errorCode = response.code()
                val errorBody = response.errorBody()?.string()
                Timber.w("safeApiCall: HTTP $errorCode — body: $errorBody")

                var message = "Erreur inconnue"
                if (!errorBody.isNullOrEmpty() && errorCode == 400) {
                    try {
                        val json = JSONObject(errorBody)
                        if (json.has("error_code") && json.has("detail")) {
                            message = json.getString("detail")
                        } else if (json.length() > 0) {
                            val firstKey = json.keys().next()
                            val value = json.get(firstKey)
                            message = if (value is org.json.JSONArray && value.length() > 0) {
                                value.getString(0)
                            } else {
                                value.toString()
                            }
                        }
                    } catch (e: Exception) {
                        message = errorBody
                    }
                }

                Result.failure(
                    when (errorCode) {
                        401  -> TripException.Unauthenticated
                        403  -> TripException.NotAssigned
                        400  -> TripException.InvalidStatus(message)
                        else -> TripException.ServerError(errorCode)
                    }
                )
            }
        } catch (e: IOException) {
            Timber.e(e, "safeApiCall: IOException (network unavailable)")
            Result.failure(TripException.NetworkUnavailable)
        } catch (e: SerializationException) {
            // JSON schema mismatch — likely DTO doesn't match server response
            Timber.e(e, "safeApiCall: SerializationException — DTO mismatch with backend response")
            Result.failure(TripException.DeserializationError(e.message ?: "Unknown"))
        } catch (e: Exception) {
            // Catch-all — log actual exception so logcat shows what really happened
            Timber.e(e, "safeApiCall: Unexpected exception — ${e.javaClass.simpleName}")
            Result.failure(TripException.ServerError(500))
        }
    }

    private suspend fun persistTripListLocally(trips: List<TripListDto>) {
        val serverIds = trips.map { it.id }
        if (serverIds.isEmpty()) {
            tripDao.clearOperationalServerTrips()
            tripReminderScheduler.syncTrips(emptyList())
            return
        }

        // Keep local operational list aligned with server scope (per logged-in user).
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
                cargoSmallPrice = existing?.cargoSmallPrice ?: 0L,
                cargoMediumPrice = existing?.cargoMediumPrice ?: 0L,
                cargoLargePrice = existing?.cargoLargePrice ?: 0L,
                currency = dto.currency,
                updatedAt = updatedAt
            )
        }

        tripDao.upsertAll(entities)
        tripReminderScheduler.syncTrips(entities)
    }

    private suspend fun persistTripDetailLocally(detail: TripDetailDto) {
        val serverTripId = detail.id
        val existing = tripDao.getByLocalOrServerId(serverTripId)

        val entity = TripEntity(
            id = existing?.id ?: serverTripId,
            serverId = serverTripId,
            originOffice = detail.originName,
            destinationOffice = detail.destinationName,
            conductorId = detail.conductor.toLong(),
            busPlate = detail.busPlate,
            status = detail.status,
            departureDateTime = parseEpochMillis(detail.departureDatetime),
            passengerBasePrice = detail.passengerBasePrice,
            cargoSmallPrice = detail.cargoSmallPrice,
            cargoMediumPrice = detail.cargoMediumPrice,
            cargoLargePrice = detail.cargoLargePrice,
            currency = detail.currency,
            updatedAt = System.currentTimeMillis()
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

    private suspend fun persistPassengerTicketsLocally(tickets: List<PassengerTicketMirrorDto>) {
        tickets.forEach { dto ->
            val localTrip = tripDao.getByLocalOrServerId(dto.tripId)
            if (localTrip == null) {
                Timber.w(
                    "TripRepositoryImpl: skip mirrored passenger ticket=%d, missing local trip for serverTrip=%d.",
                    dto.id,
                    dto.tripId,
                )
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
                Timber.w(
                    "TripRepositoryImpl: skip mirrored cargo ticket=%d, missing local trip for serverTrip=%d.",
                    dto.id,
                    dto.tripId,
                )
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

    private fun TripEntity.toTripListDto(): TripListDto {
        return TripListDto(
            id = serverId ?: id,
            origin = originOffice,
            destination = destinationOffice,
            conductor = "",
            plate = busPlate,
            departureDatetime = departureDateTime.toIsoOffsetDateTime(),
            status = status,
            passengerBasePrice = passengerBasePrice,
            currency = currency,
        )
    }

    private fun TripEntity.toTripDetailDto(): TripDetailDto {
        return TripDetailDto(
            id = serverId ?: id,
            originOffice = 0,
            destinationOffice = 0,
            conductor = conductorId.toInt(),
            bus = 0,
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
        )
    }

    private fun Long.toIsoOffsetDateTime(): String {
        return Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).toString()
    }
}
