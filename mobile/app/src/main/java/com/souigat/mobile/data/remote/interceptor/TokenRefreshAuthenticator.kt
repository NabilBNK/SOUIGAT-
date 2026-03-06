package com.souigat.mobile.data.remote.interceptor

import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.remote.api.AuthApi
import com.souigat.mobile.data.remote.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Provider

/**
 * Automatically intercepts HTTP 401 responses and attempts to refresh the access token.
 */
class TokenRefreshAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val authApiProvider: Provider<AuthApi> // must be the NO-AUTH retrofit instance provided by dagger
) : Authenticator {

    private val isRefreshing = AtomicBoolean(false)

    override fun authenticate(route: Route?, response: Response): Request? {
        // Step 1: Check if the token on the FAILED REQUEST is still the
        // current token. If another thread already refreshed it, just retry
        // with the new token without calling refresh again.
        val currentToken = tokenManager.getAccessToken()
        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")

        if (currentToken != null && currentToken != failedToken) {
            Timber.i("Token already refreshed by concurrent thread. Retrying Request: ${response.request.url.encodedPath}")
            return response.request.newBuilder()
                .header("Authorization", "Bearer $currentToken")
                .build()
        }

        // Prevent infinite loops if the refresh endpoint itself throws 401
        if (response.request.url.encodedPath.contains("token/refresh")) {
            Timber.w("Refresh token endpoint returned 401. Session expired.")
            tokenManager.clearAll()
            return null
        }

        // Limit retries
        if (responseCount(response) >= 2) {
            Timber.w("Refresh retry limit reached.")
            return null
        }

        // Step 2: Only ONE thread should attempt the refresh
        if (!isRefreshing.compareAndSet(false, true)) {
            // Another thread is refreshing — wait briefly then retry
            Timber.i("Another thread is refreshing. Waiting 500ms...")
            try { Thread.sleep(500) } catch (e: InterruptedException) { /* ignored */ }
            val newToken = tokenManager.getAccessToken()
            return if (newToken != null && newToken != failedToken) {
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
            } else null
        }

        return try {
            val refreshToken = tokenManager.getRefreshToken() ?: return null
            val deviceId = tokenManager.getDeviceId()

            Timber.i("Executing refresh API call locally under Mutex lock...")
            val refreshResponse = runBlocking {
                authApiProvider.get().refreshToken(RefreshRequest(refreshToken, deviceId))
            }

            if (refreshResponse.isSuccessful) {
                val newAccessToken = refreshResponse.body()?.access ?: return null
                val newRefreshToken = refreshResponse.body()?.refresh ?: refreshToken
                tokenManager.saveTokens(newAccessToken, newRefreshToken)
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
            } else {
                Timber.e("Refresh failed. Clearing tokens (HTTP ${refreshResponse.code()})")
                // Refresh failed — clear tokens and trigger logout event
                tokenManager.clearAll()
                null  // returning null forces OkHttp to surface the 401 to the caller
            }
        } finally {
            isRefreshing.set(false)
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var priorResponse = response.priorResponse
        while (priorResponse != null) {
            count++
            priorResponse = priorResponse.priorResponse
        }
        return count
    }
}
