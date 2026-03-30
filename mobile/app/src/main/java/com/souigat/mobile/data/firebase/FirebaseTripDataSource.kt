package com.souigat.mobile.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.domain.model.TripRouteSegmentTariff
import com.souigat.mobile.domain.model.TripRouteStop
import com.souigat.mobile.domain.model.TripDetail
import com.souigat.mobile.domain.model.TripListItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.Instant

data class PassengerTicketMirrorDto(
    val id: Long,
    val tripId: Long,
    val ticketNumber: String,
    val passengerName: String,
    val seatNumber: String,
    val price: Long,
    val currency: String,
    val paymentSource: String,
    val status: String,
    val boardingPoint: String,
    val alightingPoint: String,
    val createdAtIso: String,
)

data class CargoTicketMirrorDto(
    val id: Long,
    val tripId: Long,
    val ticketNumber: String,
    val senderName: String,
    val senderPhone: String,
    val receiverName: String,
    val receiverPhone: String,
    val cargoTier: String,
    val description: String,
    val price: Long,
    val currency: String,
    val paymentSource: String,
    val status: String,
    val createdAtIso: String,
)

data class TripExpenseMirrorDto(
    val id: Long,
    val tripId: Long,
    val description: String,
    val amount: Long,
    val currency: String,
    val category: String,
    val createdAtIso: String,
)

@Singleton
class FirebaseTripDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val tokenManager: TokenManager,
    private val firebaseSessionManager: FirebaseSessionManager
) {
    private val collection = firestore.collection("trip_mirror_v1")
    private val passengerCollection = firestore.collection("passenger_ticket_mirror_v1")
    private val cargoCollection = firestore.collection("cargo_ticket_mirror_v1")
    private val expenseCollection = firestore.collection("trip_expense_mirror_v1")

    suspend fun fetchTripList(limit: Long = 100): Result<List<TripListItem>> {
        if (!firebaseSessionManager.ensureSignedIn()) {
            return Result.failure(IllegalStateException("Firebase session is not ready."))
        }

        val query = scopedTripQuery(limit)
            ?: return Result.failure(IllegalStateException("Current user scope is not eligible for Firebase trip reads."))

        return runCatching {
            val snapshot = query.get().await()
            snapshot.documents.mapNotNull { document ->
                val data = document.data ?: return@mapNotNull null
                if (!isScopedForCurrentUser(data)) {
                    return@mapNotNull null
                }
                mapToTripListDto(document.id, data)
            }
        }.onFailure { error ->
            Timber.w(error, "FirebaseTripDataSource: failed to fetch trip list from Firestore.")
        }
    }

    suspend fun fetchTripDetail(id: Long): Result<TripDetail> {
        if (!firebaseSessionManager.ensureSignedIn()) {
            return Result.failure(IllegalStateException("Firebase session is not ready."))
        }

        return runCatching {
            val directSnapshot = collection.document(id.toString()).get().await()
            val snapshot = if (directSnapshot.exists()) {
                directSnapshot
            } else {
                collection.whereEqualTo("id", id).limit(1).get().await().documents.firstOrNull()
                    ?: throw IllegalStateException("Trip $id not found in Firestore mirror.")
            }

            val data = snapshot.data
                ?: throw IllegalStateException("Trip $id has empty mirror payload.")

            if (!isScopedForCurrentUser(data)) {
                throw IllegalStateException("Trip $id is outside current user scope.")
            }

            mapToTripDetailDto(snapshot.id, data)
                ?: throw IllegalStateException("Trip $id has incomplete mirror payload.")
        }.onFailure { error ->
            Timber.w(error, "FirebaseTripDataSource: failed to fetch trip detail from Firestore.")
        }
    }

    suspend fun updateTripStatus(tripId: Long, newStatus: String): Result<Unit> {
        if (!firebaseSessionManager.ensureSignedIn()) {
            return Result.failure(IllegalStateException("Firebase session is not ready."))
        }

        return runCatching {
            val snapshot = collection.document(tripId.toString()).get().await()
            val currentStatus = snapshot.getString("status")

            if (currentStatus == newStatus) {
                return@runCatching Unit
            }

            val validTransition = when (currentStatus) {
                "scheduled" -> newStatus == "in_progress"
                "in_progress" -> newStatus == "completed"
                else -> false
            }

            if (!validTransition) {
                throw IllegalStateException("Invalid trip status transition $currentStatus -> $newStatus")
            }

            val payload = mutableMapOf<String, Any>(
                "status" to newStatus,
                "source_updated_at" to Instant.now().toString(),
            )

            if (newStatus == "completed") {
                val nowEpoch = System.currentTimeMillis()
                payload["arrival_ts"] = nowEpoch
                payload["arrival_datetime"] = Instant.ofEpochMilli(nowEpoch).toString()
            }

            collection.document(tripId.toString())
                .set(payload, SetOptions.merge())
                .await()
            Unit
        }.onFailure { error ->
            Timber.w(error, "FirebaseTripDataSource: failed to update trip status in Firestore.")
        }
    }

    suspend fun listenTripList(
        limit: Long = 100,
        onUpdate: (List<TripListItem>) -> Unit,
        onError: (Throwable) -> Unit,
    ): Result<ListenerRegistration> {
        if (!firebaseSessionManager.ensureSignedIn()) {
            return Result.failure(IllegalStateException("Firebase session is not ready."))
        }

        val query = scopedTripQuery(limit)
            ?: return Result.failure(IllegalStateException("Current user scope is not eligible for Firebase trip reads."))

        return runCatching {
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                val trips = snapshot.documents.mapNotNull { document ->
                    val data = document.data ?: return@mapNotNull null
                    if (!isScopedForCurrentUser(data)) {
                        return@mapNotNull null
                    }
                    mapToTripListDto(document.id, data)
                }
                onUpdate(trips)
            }
        }
    }

    suspend fun listenPassengerTickets(
        limit: Long = 500,
        onUpdate: (List<PassengerTicketMirrorDto>) -> Unit,
        onError: (Throwable) -> Unit,
    ): Result<ListenerRegistration> {
        if (!firebaseSessionManager.ensureSignedIn()) {
            return Result.failure(IllegalStateException("Firebase session is not ready."))
        }

        val query = scopedMirrorQuery(passengerCollection, limit)
            ?: return Result.failure(IllegalStateException("Current user scope is not eligible for Firebase passenger ticket reads."))

        return runCatching {
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                val tickets = snapshot.documents.mapNotNull { document ->
                    val data = document.data ?: return@mapNotNull null
                    if (!isScopedForCurrentUser(data)) {
                        return@mapNotNull null
                    }
                    mapToPassengerTicketMirrorDto(data)
                }
                onUpdate(tickets)
            }
        }
    }

    suspend fun listenCargoTickets(
        limit: Long = 500,
        onUpdate: (List<CargoTicketMirrorDto>) -> Unit,
        onError: (Throwable) -> Unit,
    ): Result<ListenerRegistration> {
        if (!firebaseSessionManager.ensureSignedIn()) {
            return Result.failure(IllegalStateException("Firebase session is not ready."))
        }

        val query = scopedMirrorQuery(cargoCollection, limit)
            ?: return Result.failure(IllegalStateException("Current user scope is not eligible for Firebase cargo ticket reads."))

        return runCatching {
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                val tickets = snapshot.documents.mapNotNull { document ->
                    val data = document.data ?: return@mapNotNull null
                    if (!isScopedForCurrentUser(data)) {
                        return@mapNotNull null
                    }
                    mapToCargoTicketMirrorDto(data)
                }
                onUpdate(tickets)
            }
        }
    }

    suspend fun listenTripExpenses(
        limit: Long = 500,
        onUpdate: (List<TripExpenseMirrorDto>) -> Unit,
        onError: (Throwable) -> Unit,
    ): Result<ListenerRegistration> {
        if (!firebaseSessionManager.ensureSignedIn()) {
            return Result.failure(IllegalStateException("Firebase session is not ready."))
        }

        val query = scopedMirrorQuery(expenseCollection, limit)
            ?: return Result.failure(IllegalStateException("Current user scope is not eligible for Firebase expense reads."))

        return runCatching {
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                val expenses = snapshot.documents.mapNotNull { document ->
                    val data = document.data ?: return@mapNotNull null
                    if (!isScopedForCurrentUser(data)) {
                        return@mapNotNull null
                    }
                    mapToTripExpenseMirrorDto(data)
                }
                onUpdate(expenses)
            }
        }
    }

    suspend fun fetchPassengerTicketsForTrip(
        tripId: Long,
        limit: Long = 500,
    ): Result<List<PassengerTicketMirrorDto>> {
        if (!firebaseSessionManager.ensureSignedIn()) {
            return Result.failure(IllegalStateException("Firebase session is not ready."))
        }

        val query = scopedMirrorQuery(passengerCollection, limit)
            ?.whereEqualTo("trip_id", tripId)
            ?: return Result.failure(IllegalStateException("Current user scope is not eligible for Firebase passenger ticket reads."))

        return runCatching {
            val snapshot = query.get().await()
            snapshot.documents.mapNotNull { document ->
                val data = document.data ?: return@mapNotNull null
                if (!isScopedForCurrentUser(data)) {
                    return@mapNotNull null
                }
                mapToPassengerTicketMirrorDto(data)
            }
        }
    }

    suspend fun fetchCargoTicketsForTrip(
        tripId: Long,
        limit: Long = 500,
    ): Result<List<CargoTicketMirrorDto>> {
        if (!firebaseSessionManager.ensureSignedIn()) {
            return Result.failure(IllegalStateException("Firebase session is not ready."))
        }

        val query = scopedMirrorQuery(cargoCollection, limit)
            ?.whereEqualTo("trip_id", tripId)
            ?: return Result.failure(IllegalStateException("Current user scope is not eligible for Firebase cargo ticket reads."))

        return runCatching {
            val snapshot = query.get().await()
            snapshot.documents.mapNotNull { document ->
                val data = document.data ?: return@mapNotNull null
                if (!isScopedForCurrentUser(data)) {
                    return@mapNotNull null
                }
                mapToCargoTicketMirrorDto(data)
            }
        }
    }

    suspend fun fetchTripExpensesForTrip(
        tripId: Long,
        limit: Long = 500,
    ): Result<List<TripExpenseMirrorDto>> {
        if (!firebaseSessionManager.ensureSignedIn()) {
            return Result.failure(IllegalStateException("Firebase session is not ready."))
        }

        val query = scopedMirrorQuery(expenseCollection, limit)
            ?.whereEqualTo("trip_id", tripId)
            ?: return Result.failure(IllegalStateException("Current user scope is not eligible for Firebase expense reads."))

        return runCatching {
            val snapshot = query.get().await()
            snapshot.documents.mapNotNull { document ->
                val data = document.data ?: return@mapNotNull null
                if (!isScopedForCurrentUser(data)) {
                    return@mapNotNull null
                }
                mapToTripExpenseMirrorDto(data)
            }
        }
    }

    private fun scopedTripQuery(limit: Long): Query? {
        val role = tokenManager.getUserRole()
        val officeId = tokenManager.getOfficeId()
        val userId = tokenManager.getUserId()

        val base = when (role) {
            "admin" -> {
                collection.whereEqualTo("is_deleted", false)
            }
            "office_staff" -> {
                if (officeId == null) {
                    collection.whereEqualTo("is_deleted", false)
                } else {
                    collection
                        .whereArrayContains("office_scope_ids", officeId)
                        .whereEqualTo("is_deleted", false)
                }
            }
            "conductor" -> {
                if (userId == null) {
                    collection.whereEqualTo("is_deleted", false)
                } else {
                    collection
                        .whereEqualTo("conductor_id", userId)
                        .whereEqualTo("is_deleted", false)
                }
            }
            else -> collection.whereEqualTo("is_deleted", false)
        }

        return base.orderBy("departure_ts", Query.Direction.DESCENDING).limit(limit)
    }

    private fun scopedMirrorQuery(
        mirrorCollection: com.google.firebase.firestore.CollectionReference,
        limit: Long,
    ): Query? {
        val role = tokenManager.getUserRole()
        val officeId = tokenManager.getOfficeId()
        val userId = tokenManager.getUserId()

        val base = when (role) {
            "admin" -> {
                mirrorCollection.whereEqualTo("is_deleted", false)
            }

            "office_staff" -> {
                if (officeId == null) {
                    mirrorCollection.whereEqualTo("is_deleted", false)
                } else {
                    mirrorCollection
                        .whereArrayContains("office_scope_ids", officeId)
                        .whereEqualTo("is_deleted", false)
                }
            }

            "conductor" -> {
                if (userId == null) {
                    mirrorCollection.whereEqualTo("is_deleted", false)
                } else {
                    mirrorCollection
                        .whereEqualTo("conductor_id", userId)
                        .whereEqualTo("is_deleted", false)
                }
            }

            else -> mirrorCollection.whereEqualTo("is_deleted", false)
        }

        return base.orderBy("source_created_at", Query.Direction.DESCENDING).limit(limit)
    }

    private fun isScopedForCurrentUser(data: Map<String, Any>): Boolean {
        val role = tokenManager.getUserRole() ?: return false
        val officeId = tokenManager.getOfficeId()
        val userId = tokenManager.getUserId()

        if (data.booleanValue("is_deleted") == true) {
            return false
        }

        return when (role) {
            "admin" -> true
            "office_staff" -> {
                if (officeId == null) {
                    false
                } else {
                    data.intListValue("office_scope_ids").contains(officeId)
                }
            }
            "conductor" -> {
                if (userId == null) {
                    false
                } else {
                    data.intValue("conductor_id") == userId
                }
            }
            else -> false
        }
    }

    private fun mapToTripListDto(documentId: String, data: Map<String, Any>): TripListItem? {
        if (data.booleanValue("is_deleted") == true) {
            return null
        }

        val id = documentId.toLongOrNull() ?: data.longValue("id") ?: return null
        val departureDatetime = data.stringValue("departure_datetime") ?: return null

        return TripListItem(
            id = id,
            origin = data.stringValue("origin_office_name") ?: "",
            destination = data.stringValue("destination_office_name") ?: "",
            conductorName = data.stringValue("conductor_name") ?: "",
            plate = data.stringValue("bus_plate") ?: "",
            departureDatetime = departureDatetime,
            status = data.stringValue("status") ?: "scheduled",
            passengerBasePrice = data.longValue("passenger_base_price") ?: 0L,
            cargoSmallPrice = data.longValue("cargo_small_price"),
            cargoMediumPrice = data.longValue("cargo_medium_price"),
            cargoLargePrice = data.longValue("cargo_large_price"),
            currency = data.stringValue("currency") ?: "DZD"
        )
    }

    private fun mapToTripDetailDto(documentId: String, data: Map<String, Any>): TripDetail? {
        if (data.booleanValue("is_deleted") == true) {
            return null
        }

        val id = documentId.toLongOrNull() ?: data.longValue("id") ?: return null
        val originOffice = data.intValue("origin_office_id") ?: return null
        val destinationOffice = data.intValue("destination_office_id") ?: return null
        val conductor = data.intValue("conductor_id") ?: return null
        val bus = data.intValue("bus_id") ?: return null
        val departureDatetime = data.stringValue("departure_datetime") ?: return null

        return TripDetail(
            id = id,
            originOfficeId = originOffice,
            destinationOfficeId = destinationOffice,
            conductorId = conductor,
            busId = bus,
            departureDatetime = departureDatetime,
            arrivalDatetime = data.stringValue("arrival_datetime"),
            status = data.stringValue("status") ?: "scheduled",
            passengerBasePrice = data.longValue("passenger_base_price") ?: 0L,
            cargoSmallPrice = data.longValue("cargo_small_price") ?: 0L,
            cargoMediumPrice = data.longValue("cargo_medium_price") ?: 0L,
            cargoLargePrice = data.longValue("cargo_large_price") ?: 0L,
            currency = data.stringValue("currency") ?: "DZD",
            conductorName = data.stringValue("conductor_name") ?: "",
            busPlate = data.stringValue("bus_plate") ?: "",
            originName = data.stringValue("origin_office_name") ?: "",
            destinationName = data.stringValue("destination_office_name") ?: "",
            routeTemplateName = data.stringValue("route_template_name") ?: "",
            routeStops = mapToTripRouteStops(data),
            routeSegmentTariffs = mapToTripRouteSegmentTariffs(data),
        )
    }

    private fun mapToPassengerTicketMirrorDto(data: Map<String, Any>): PassengerTicketMirrorDto? {
        if (data.booleanValue("is_deleted") == true) {
            return null
        }

        val id = data.longValue("id") ?: return null
        val tripId = data.longValue("trip_id") ?: return null
        val sourceCreatedAt = data.stringValue("source_created_at")
            ?: data.stringValue("source_updated_at")
            ?: return null

        return PassengerTicketMirrorDto(
            id = id,
            tripId = tripId,
            ticketNumber = data.stringValue("ticket_number") ?: "PT-$tripId-$id",
            passengerName = data.stringValue("passenger_name") ?: "Passager",
            seatNumber = data.stringValue("seat_number") ?: "",
            price = data.longValue("price") ?: 0L,
            currency = data.stringValue("currency") ?: "DZD",
            paymentSource = data.stringValue("payment_source") ?: "cash",
            status = data.stringValue("status") ?: "active",
            boardingPoint = data.stringValue("boarding_point") ?: "",
            alightingPoint = data.stringValue("alighting_point") ?: "",
            createdAtIso = sourceCreatedAt,
        )
    }

    private fun mapToCargoTicketMirrorDto(data: Map<String, Any>): CargoTicketMirrorDto? {
        if (data.booleanValue("is_deleted") == true) {
            return null
        }

        val id = data.longValue("id") ?: return null
        val tripId = data.longValue("trip_id") ?: return null
        val sourceCreatedAt = data.stringValue("source_created_at")
            ?: data.stringValue("source_updated_at")
            ?: return null

        return CargoTicketMirrorDto(
            id = id,
            tripId = tripId,
            ticketNumber = data.stringValue("ticket_number") ?: "CT-$tripId-$id",
            senderName = data.stringValue("sender_name") ?: "",
            senderPhone = data.stringValue("sender_phone") ?: "",
            receiverName = data.stringValue("receiver_name") ?: "",
            receiverPhone = data.stringValue("receiver_phone") ?: "",
            cargoTier = data.stringValue("cargo_tier") ?: "small",
            description = data.stringValue("description") ?: "",
            price = data.longValue("price") ?: 0L,
            currency = data.stringValue("currency") ?: "DZD",
            paymentSource = data.stringValue("payment_source") ?: "prepaid",
            status = data.stringValue("status") ?: "created",
            createdAtIso = sourceCreatedAt,
        )
    }

    private fun mapToTripExpenseMirrorDto(data: Map<String, Any>): TripExpenseMirrorDto? {
        if (data.booleanValue("is_deleted") == true) {
            return null
        }

        val id = data.longValue("id") ?: return null
        val tripId = data.longValue("trip_id") ?: return null
        val sourceCreatedAt = data.stringValue("source_created_at")
            ?: data.stringValue("source_updated_at")
            ?: return null

        return TripExpenseMirrorDto(
            id = id,
            tripId = tripId,
            description = data.stringValue("description") ?: "",
            amount = data.longValue("amount") ?: 0L,
            currency = data.stringValue("currency") ?: "DZD",
            category = data.stringValue("category") ?: "other",
            createdAtIso = sourceCreatedAt,
        )
    }
}

private fun mapToTripRouteStops(data: Map<String, Any>): List<TripRouteStop> {
    val raw = data["route_stop_snapshot"]
    if (raw !is List<*>) {
        return emptyList()
    }
    return raw.mapNotNull { item ->
        val stop = item as? Map<*, *> ?: return@mapNotNull null
        val stopName = (stop["stop_name"] as? String)?.trim().orEmpty()
        val officeName = (stop["office_name"] as? String)?.trim().orEmpty()
        val displayName = when {
            stopName.isNotBlank() -> stopName
            officeName.isNotBlank() -> officeName
            else -> return@mapNotNull null
        }
        val officeId = numberToInt(stop["office_id"]) ?: -1
        val stopOrder = numberToInt(stop["stop_order"]) ?: return@mapNotNull null
        TripRouteStop(
            officeId = officeId,
            officeName = displayName,
            stopOrder = stopOrder,
        )
    }.sortedBy { it.stopOrder }
}

private fun mapToTripRouteSegmentTariffs(data: Map<String, Any>): List<TripRouteSegmentTariff> {
    val raw = data["route_segment_tariff_snapshot"]
    if (raw !is List<*>) {
        return emptyList()
    }
    return raw.mapNotNull { item ->
        val segment = item as? Map<*, *> ?: return@mapNotNull null
        val fromStopOrder = numberToInt(segment["from_stop_order"]) ?: return@mapNotNull null
        val toStopOrder = numberToInt(segment["to_stop_order"]) ?: return@mapNotNull null
        val passengerPrice = numberToLong(segment["passenger_price"]) ?: return@mapNotNull null
        val currency = segment["currency"] as? String ?: "DZD"
        TripRouteSegmentTariff(
            fromStopOrder = fromStopOrder,
            toStopOrder = toStopOrder,
            passengerPrice = passengerPrice,
            currency = currency,
        )
    }.sortedBy { it.fromStopOrder }
}

private fun numberToLong(value: Any?): Long? {
    return when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        is Float -> value.toLong()
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
}

private fun numberToInt(value: Any?): Int? = numberToLong(value)?.toInt()

private fun Map<String, Any>.stringValue(key: String): String? {
    val raw = this[key] ?: return null
    return raw as? String
}

private fun Map<String, Any>.booleanValue(key: String): Boolean? {
    val raw = this[key] ?: return null
    return raw as? Boolean
}

private fun Map<String, Any>.longValue(key: String): Long? {
    val raw = this[key] ?: return null
    return when (raw) {
        is Long -> raw
        is Int -> raw.toLong()
        is Double -> raw.toLong()
        is Float -> raw.toLong()
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull()
        else -> null
    }
}

private fun Map<String, Any>.intValue(key: String): Int? {
    return longValue(key)?.toInt()
}

private fun Map<String, Any>.intListValue(key: String): List<Int> {
    val raw = this[key] ?: return emptyList()
    if (raw !is List<*>) {
        return emptyList()
    }

    return raw.mapNotNull { item ->
        when (item) {
            is Int -> item
            is Long -> item.toInt()
            is Double -> item.toInt()
            is Float -> item.toInt()
            is Number -> item.toInt()
            is String -> item.toIntOrNull()
            else -> null
        }
    }
}
