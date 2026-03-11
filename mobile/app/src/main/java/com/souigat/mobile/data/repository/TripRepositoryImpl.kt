package com.souigat.mobile.data.repository

import com.souigat.mobile.data.remote.api.TripApi
import com.souigat.mobile.data.remote.dto.TripDetailDto
import com.souigat.mobile.data.remote.dto.TripListDto
import com.souigat.mobile.data.remote.dto.TripStatusDto
import com.souigat.mobile.domain.repository.TripException
import com.souigat.mobile.domain.repository.TripRepository
import kotlinx.serialization.SerializationException
import org.json.JSONObject
import retrofit2.Response
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepositoryImpl @Inject constructor(
    private val tripApi: TripApi
) : TripRepository {

    override suspend fun getTripList(): Result<List<TripListDto>> {
        val result = safeApiCall { tripApi.getTripList() }
        return result.map { it.results }
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
}
