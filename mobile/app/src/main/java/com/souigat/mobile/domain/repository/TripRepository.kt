package com.souigat.mobile.domain.repository

import com.souigat.mobile.data.remote.dto.TripDetailDto
import com.souigat.mobile.data.remote.dto.TripListDto
import com.souigat.mobile.data.remote.dto.TripStatusDto

interface TripRepository {
    suspend fun getTripList(): Result<List<TripListDto>>
    suspend fun getTripDetail(id: Long): Result<TripDetailDto>
    suspend fun startTrip(id: Long): Result<TripStatusDto>
    suspend fun completeTrip(id: Long): Result<TripStatusDto>
    suspend fun refreshTripActivity(id: Long): Result<Unit>
    suspend fun startRealtimeTripSync(): Result<Unit>
    fun stopRealtimeTripSync()
}

// Typed error hierarchy for Trip operations
sealed class TripException : Exception() {
    object NotAssigned : TripException()          // 403 Forbidden
    object Unauthenticated : TripException()      // 401 Unauthorized — token expired, refresh failed
    data class InvalidStatus(override val message: String) : TripException() // 400 Bad Request
    object NetworkUnavailable : TripException()   // IOException
    data class ServerError(val code: Int) : TripException()
    data class DeserializationError(val detail: String) : TripException() // DTO ↔ backend schema mismatch
}
