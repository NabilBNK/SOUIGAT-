package com.souigat.mobile.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BackendConnectionState {
    Checking,
    Online,
    BackendUnavailable,
    Offline
}

@Singleton
class BackendConnectionMonitor @Inject constructor(
    @ApplicationContext context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<BackendConnectionState> = _state.asStateFlow()

    init {
        connectivityManager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (_state.value == BackendConnectionState.Offline) {
                        _state.value = BackendConnectionState.Online
                    }
                }

                override fun onLost(network: Network) {
                    _state.value = BackendConnectionState.Offline
                }

                override fun onUnavailable() {
                    _state.value = BackendConnectionState.Offline
                }
            }
        )
    }

    fun markBackendSuccess() {
        if (_state.value != BackendConnectionState.Offline) {
            _state.value = BackendConnectionState.Online
        }
    }

    fun markBackendFailure() {
        // Local-first mobile should continue operating from SQLite/Firebase even when
        // Django API is temporarily unreachable. If internet exists, keep the UX online.
        _state.value = if (hasInternet()) {
            BackendConnectionState.Online
        } else {
            BackendConnectionState.Offline
        }
    }

    private fun initialState(): BackendConnectionState {
        return if (hasInternet()) {
            BackendConnectionState.Online
        } else {
            BackendConnectionState.Offline
        }
    }

    private fun hasInternet(): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
