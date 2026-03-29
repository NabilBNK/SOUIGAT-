package com.souigat.mobile.domain.repository

import com.souigat.mobile.domain.model.TripDetail
import com.souigat.mobile.domain.model.TripListItem
import com.souigat.mobile.domain.model.TripStatusResult

interface TripRepository {
    suspend fun getTripList(): Result<List<TripListItem>>
    suspend fun getTripDetail(id: Long): Result<TripDetail>
    suspend fun startTrip(id: Long): Result<TripStatusResult>
    suspend fun completeTrip(id: Long): Result<TripStatusResult>
    suspend fun refreshTripActivity(id: Long): Result<Unit>
    suspend fun startRealtimeTripSync(): Result<Unit>
    fun stopRealtimeTripSync()
}

sealed class TripException : Exception() {
    object NotAssigned : TripException()
    object Unauthenticated : TripException()
    data class InvalidStatus(override val message: String) : TripException()
    object NetworkUnavailable : TripException()
    data class DataError(val detail: String) : TripException()
}

