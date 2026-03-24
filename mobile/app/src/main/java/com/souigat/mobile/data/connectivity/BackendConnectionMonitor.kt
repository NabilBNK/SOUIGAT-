package com.souigat.mobile.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BackendConnectionState {
    Checking,
    Online,
    BackendUnavailable,
    Offline
}

@Singleton
class BackendConnectionMonitor @Inject constructor(
    @ApplicationContext context: Context,
    private val backendEndpointResolver: BackendEndpointResolver
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<BackendConnectionState> = _state.asStateFlow()

    init {
        connectivityManager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        backendEndpointResolver.resolveBaseUrl(forceRefresh = true)
                    }
                    if (_state.value == BackendConnectionState.Offline) {
                        _state.value = BackendConnectionState.Online
                    }
                }

                override fun onLost(network: Network) {
                    backendEndpointResolver.invalidateResolvedBaseUrl()
                    _state.value = BackendConnectionState.Offline
                }

                override fun onUnavailable() {
                    backendEndpointResolver.invalidateResolvedBaseUrl()
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
        if (_state.value != BackendConnectionState.Offline) {
            _state.value = BackendConnectionState.BackendUnavailable
        }
    }

    private fun initialState(): BackendConnectionState {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val hasNetwork = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        return if (hasNetwork) {
            BackendConnectionState.Online
        } else {
            BackendConnectionState.Offline
        }
    }
}
