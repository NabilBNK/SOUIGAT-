package com.souigat.mobile.domain.repository

import com.souigat.mobile.data.remote.dto.TripDetailDto
import com.souigat.mobile.data.remote.dto.TripListDto
import com.souigat.mobile.data.remote.dto.TripStatusDto

interface TripRepository {
    suspend fun getTripList(): Result<List<TripListDto>>
    suspend fun getTripDetail(id: Int): Result<TripDetailDto>
    suspend fun startTrip(id: Int): Result<TripStatusDto>
    suspend fun completeTrip(id: Int): Result<TripStatusDto>
}

// Typed error hierarchy for Trip operations
sealed class TripException : Exception() {
    object NotAssigned : TripException()          // 403 Forbidden
    data class InvalidStatus(override val message: String) : TripException() // 400 Bad Request
    object NetworkUnavailable : TripException()   // IOException
    data class ServerError(val code: Int) : TripException()
}
