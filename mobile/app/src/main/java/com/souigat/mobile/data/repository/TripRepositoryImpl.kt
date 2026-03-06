package com.souigat.mobile.data.repository

import com.souigat.mobile.data.remote.api.TripApi
import com.souigat.mobile.data.remote.dto.TripDetailDto
import com.souigat.mobile.data.remote.dto.TripListDto
import com.souigat.mobile.data.remote.dto.TripStatusDto
import com.souigat.mobile.domain.repository.TripException
import com.souigat.mobile.domain.repository.TripRepository
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepositoryImpl @Inject constructor(
    private val tripApi: TripApi
) : TripRepository {

    override suspend fun getTripList(): Result<List<TripListDto>> {
        return safeApiCall { tripApi.getTripList() }
    }

    override suspend fun getTripDetail(id: Int): Result<TripDetailDto> {
        return safeApiCall { tripApi.getTripDetail(id) }
    }

    override suspend fun startTrip(id: Int): Result<TripStatusDto> {
        return safeApiCall { tripApi.startTrip(id) }
    }

    override suspend fun completeTrip(id: Int): Result<TripStatusDto> {
        return safeApiCall { tripApi.completeTrip(id) }
    }

    private suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): Result<T> {
        return try {
            val response = apiCall()
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                val errorCode = response.code()
                val errorBody = response.errorBody()?.string()
                
                // Parse message from generic DRF 400 Bad Request if available
                var message = "Erreur inconnue"
                if (!errorBody.isNullOrEmpty() && errorCode == 400) {
                    try {
                        val json = JSONObject(errorBody)
                        if (json.length() > 0) {
                            // Extract first error message
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
                        403 -> TripException.NotAssigned
                        400 -> TripException.InvalidStatus(message)
                        else -> TripException.ServerError(errorCode)
                    }
                )
            }
        } catch (e: IOException) {
            Result.failure(TripException.NetworkUnavailable)
        } catch (e: Exception) {
            Result.failure(TripException.ServerError(500))
        }
    }
}
