package com.souigat.mobile.data.remote.interceptor

import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.remote.api.AuthApi
import com.souigat.mobile.data.remote.dto.RefreshRequest
import kotlinx.coroutines.Dispatchers
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

    override fun authenticate(route: Route?, response: Response): Request? = synchronized(RefreshLock) {
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

        // Attempt refresh — this is now single-threaded due to @Synchronized
        val refreshToken = tokenManager.getRefreshToken() ?: run {
            tokenManager.clearAll()
            return null
        }
        val deviceId = tokenManager.getDeviceId()

        Timber.i("Executing refresh API call locally under Synchronized lock...")
        val refreshResponse = runBlocking(Dispatchers.IO) {
            authApiProvider.get().refreshToken(RefreshRequest(refreshToken, deviceId))
        }

        return if (refreshResponse.isSuccessful) {
            val newAccessToken = refreshResponse.body()?.access ?: return null
            val newRefreshToken = refreshResponse.body()?.refresh ?: refreshToken
            tokenManager.saveTokens(newAccessToken, newRefreshToken)
            response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        } else {
            Timber.e("Refresh failed. Clearing tokens (HTTP ${refreshResponse.code()})")
            tokenManager.clearAll()
            null
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
