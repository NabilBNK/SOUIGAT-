package com.souigat.mobile.data.remote.api

import com.souigat.mobile.data.remote.dto.TripDetailDto
import com.souigat.mobile.data.remote.dto.TripListDto
import com.souigat.mobile.data.remote.dto.TripStatusDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class PaginatedTripListDto(
    val count: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<TripListDto>
)

interface TripApi {

    @GET("trips/")
    suspend fun getTripList(
        @Query("status") statusFilter: String? = "scheduled,in_progress"
    ): Response<PaginatedTripListDto>

    @GET("trips/{id}/")
    suspend fun getTripDetail(
        @Path("id") id: Long
    ): Response<TripDetailDto>

    @POST("trips/{id}/start/")
    suspend fun startTrip(
        @Path("id") id: Long
    ): Response<TripStatusDto>

    @POST("trips/{id}/complete/")
    suspend fun completeTrip(
        @Path("id") id: Long
    ): Response<TripStatusDto>
}
