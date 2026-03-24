package com.souigat.mobile.ui.screens.boot

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

sealed class BootState {
    object Loading : BootState()
    object RequireLogin : BootState()
    data class Authenticated(val role: String) : BootState()
}

@HiltViewModel
class BootViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _bootState = MutableStateFlow<BootState>(BootState.Loading)
    val bootState: StateFlow<BootState> = _bootState.asStateFlow()

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            val accessToken = tokenManager.getAccessToken()
            val refreshToken = tokenManager.getRefreshToken()
            val role = tokenManager.getUserRole() ?: "conductor" // default fallback

            if (accessToken == null || refreshToken == null) {
                _bootState.value = BootState.RequireLogin
                return@launch
            }

            if (isTokenValid(accessToken)) {
                Timber.i("Boot: Valid access token found. Logging in offline as $role.")
                _bootState.value = BootState.Authenticated(role)
            } else if (isTokenValid(refreshToken)) {
                // Access expired, but refresh is valid. We can proceed to dashboard and let OkHttp interceptors
                // handle the silent refresh on the very first API call, ensuring true offline launch capability!
                Timber.i("Boot: Access expired, valid refresh token found. Silent refresh deferred. Logging in offline as $role.")
                _bootState.value = BootState.Authenticated(role)
            } else {
                Timber.w("Boot: Both tokens expired. Forcing relogin.")
                tokenManager.clearAll()
                _bootState.value = BootState.RequireLogin
            }
        }
    }

    private fun isTokenValid(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return false

            val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE)
            val jsonObject = JSONObject(String(payloadBytes, Charsets.UTF_8))

            if (!jsonObject.has("exp")) return false

            val expTimestamp = jsonObject.getLong("exp")
            val currentTimestamp = System.currentTimeMillis() / 1000L

            // Valid if it expires in more than 60 seconds
            expTimestamp - currentTimestamp > 60
        } catch (e: Exception) {
            false
        }
    }
}
