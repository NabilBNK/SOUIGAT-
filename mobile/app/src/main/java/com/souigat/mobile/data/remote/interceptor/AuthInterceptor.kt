package com.souigat.mobile.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Injects JWT access token into API requests.
 *
 * TODO Phase 3.1: Inject AuthRepository and read token from EncryptedSharedPreferences.
 *                 For now, this is a stub that passes requests through unchanged.
 */
class AuthInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // TODO Phase 3.1: Uncomment and implement token retrieval
        // val token = authRepository.getAccessToken() ?: return chain.proceed(originalRequest)
        // val authenticated = originalRequest.newBuilder()
        //     .header("Authorization", "Bearer $token")
        //     .build()
        // return chain.proceed(authenticated)

        return chain.proceed(originalRequest)
    }
}
