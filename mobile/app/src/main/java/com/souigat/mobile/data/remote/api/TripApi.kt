package com.souigat.mobile.data.remote.api

import com.souigat.mobile.data.remote.dto.TripDetailDto
import com.souigat.mobile.data.remote.dto.TripListDto
import com.souigat.mobile.data.remote.dto.TripStatusDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TripApi {

    @GET("trips/")
    suspend fun getTripList(
        @Query("status") statusFilter: String? = "scheduled,in_progress"
    ): Response<List<TripListDto>>

    @GET("trips/{id}/")
    suspend fun getTripDetail(
        @Path("id") id: Int
    ): Response<TripDetailDto>

    @POST("trips/{id}/start/")
    suspend fun startTrip(
        @Path("id") id: Int
    ): Response<TripStatusDto>

    @POST("trips/{id}/complete/")
    suspend fun completeTrip(
        @Path("id") id: Int
    ): Response<TripStatusDto>
}
