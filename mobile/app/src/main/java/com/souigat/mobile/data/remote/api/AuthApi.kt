package com.souigat.mobile.data.remote.api

import com.souigat.mobile.data.remote.dto.LoginRequest
import com.souigat.mobile.data.remote.dto.LoginResponse
import com.souigat.mobile.data.remote.dto.LogoutRequest
import com.souigat.mobile.data.remote.dto.FirebaseLoginRequest
import com.souigat.mobile.data.remote.dto.FirebaseCustomTokenRequest
import com.souigat.mobile.data.remote.dto.FirebaseCustomTokenResponse
import com.souigat.mobile.data.remote.dto.RefreshRequest
import com.souigat.mobile.data.remote.dto.RefreshResponse
import com.souigat.mobile.data.remote.dto.UserProfileDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login/")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/firebase-login/")
    suspend fun firebaseLogin(@Body request: FirebaseLoginRequest): Response<LoginResponse>

    @POST("auth/token/refresh/")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<RefreshResponse>

    @POST("auth/logout/")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>

    @GET("auth/me/")
    suspend fun getMe(): Response<UserProfileDto>

    @POST("auth/firebase-token/")
    suspend fun getFirebaseCustomToken(
        @Header("Authorization") authorization: String,
        @Body request: FirebaseCustomTokenRequest,
    ): Response<FirebaseCustomTokenResponse>
}
