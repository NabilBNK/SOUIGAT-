package com.souigat.mobile.data.remote.interceptor

import com.souigat.mobile.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject

/**
 * Shared lock used by TokenRefreshAuthenticator to serialize refresh requests.
 */
internal object RefreshLock

/**
 * Injects JWT access token into protected API requests.
 */
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Never add bearer tokens on public authentication endpoints.
        if (isPublicAuthEndpoint(originalRequest)) {
            return chain.proceed(originalRequest)
        }

        // Respect an explicitly provided Authorization header.
        if (originalRequest.header("Authorization") != null) {
            return chain.proceed(originalRequest)
        }

        val token = tokenManager.getAccessToken() ?: return chain.proceed(originalRequest)

        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(authenticatedRequest)
    }

    private fun isPublicAuthEndpoint(request: Request): Boolean {
        val path = request.url.encodedPath
        return path.endsWith("/auth/login/") || path.endsWith("/auth/token/refresh/")
    }
}
