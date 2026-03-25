package com.souigat.mobile.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.remote.dto.TripDetailDto
import com.souigat.mobile.data.remote.dto.TripListDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@Singleton
class FirebaseTripDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val tokenManager: TokenManager,
    private val firebaseSessionManager: FirebaseSessionManager
) {
    private val collection = firestore.collection("trip_mirror_v1")

    suspend fun fetchTripList(limit: Long = 100): Result<List<TripListDto>> {
        if (!firebaseSessionManager.ensureSignedIn()) {
            return Result.failure(IllegalStateException("Firebase session is not ready."))
        }

        val query = scopedTripQuery(limit)
            ?: return Result.failure(IllegalStateException("Current user scope is not eligible for Firebase trip reads."))

        return runCatching {
            val snapshot = query.get().await()
            snapshot.documents.mapNotNull { document ->
                val data = document.data ?: return@mapNotNull null
                mapToTripListDto(data)
            }
        }.onFailure { error ->
            Timber.w(error, "FirebaseTripDataSource: failed to fetch trip list from Firestore.")
        }
    }

    suspend fun fetchTripDetail(id: Long): Result<TripDetailDto> {
        if (!firebaseSessionManager.ensureSignedIn()) {
            return Result.failure(IllegalStateException("Firebase session is not ready."))
        }

        return runCatching {
            val snapshot = collection.document(id.toString()).get().await()
            val data = snapshot.data
                ?: throw IllegalStateException("Trip $id not found in Firestore mirror.")

            if (!isScopedForCurrentUser(data)) {
                throw IllegalStateException("Trip $id is outside current user scope.")
            }

            mapToTripDetailDto(data)
                ?: throw IllegalStateException("Trip $id has incomplete mirror payload.")
        }.onFailure { error ->
            Timber.w(error, "FirebaseTripDataSource: failed to fetch trip detail from Firestore.")
        }
    }

    private fun scopedTripQuery(limit: Long): Query? {
        val role = tokenManager.getUserRole() ?: return null
        val officeId = tokenManager.getOfficeId()
        val userId = tokenManager.getUserId()

        val base = when (role) {
            "admin" -> {
                collection.whereEqualTo("is_deleted", false)
            }
            "office_staff" -> {
                if (officeId == null) return null
                collection
                    .whereArrayContains("office_scope_ids", officeId)
                    .whereEqualTo("is_deleted", false)
            }
            "conductor" -> {
                if (userId == null) return null
                collection
                    .whereEqualTo("conductor_id", userId)
                    .whereEqualTo("is_deleted", false)
            }
            else -> return null
        }

        return base.orderBy("departure_ts", Query.Direction.DESCENDING).limit(limit)
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

    private fun mapToTripListDto(data: Map<String, Any>): TripListDto? {
        if (data.booleanValue("is_deleted") == true) {
            return null
        }

        val id = data.longValue("id") ?: return null
        val departureDatetime = data.stringValue("departure_datetime") ?: return null

        return TripListDto(
            id = id,
            origin = data.stringValue("origin_office_name") ?: "",
            destination = data.stringValue("destination_office_name") ?: "",
            conductor = data.stringValue("conductor_name") ?: "",
            plate = data.stringValue("bus_plate") ?: "",
            departureDatetime = departureDatetime,
            status = data.stringValue("status") ?: "scheduled",
            passengerBasePrice = data.longValue("passenger_base_price") ?: 0L,
            currency = data.stringValue("currency") ?: "DZD"
        )
    }

    private fun mapToTripDetailDto(data: Map<String, Any>): TripDetailDto? {
        if (data.booleanValue("is_deleted") == true) {
            return null
        }

        val id = data.longValue("id") ?: return null
        val originOffice = data.intValue("origin_office_id") ?: return null
        val destinationOffice = data.intValue("destination_office_id") ?: return null
        val conductor = data.intValue("conductor_id") ?: return null
        val bus = data.intValue("bus_id") ?: return null
        val departureDatetime = data.stringValue("departure_datetime") ?: return null

        return TripDetailDto(
            id = id,
            originOffice = originOffice,
            destinationOffice = destinationOffice,
            conductor = conductor,
            bus = bus,
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
            destinationName = data.stringValue("destination_office_name") ?: ""
        )
    }
}

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
