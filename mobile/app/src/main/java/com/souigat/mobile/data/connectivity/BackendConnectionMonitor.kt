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

enum class AppConnectionState {
    Checking,
    Online,
    Offline,
}

@Singleton
class AppConnectionMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<AppConnectionState> = _state.asStateFlow()

    init {
        connectivityManager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _state.value = AppConnectionState.Online
                }

                override fun onLost(network: Network) {
                    _state.value = AppConnectionState.Offline
                }

                override fun onUnavailable() {
                    _state.value = AppConnectionState.Offline
                }
            },
        )
    }

    private fun initialState(): AppConnectionState {
        return if (hasInternet()) {
            AppConnectionState.Online
        } else {
            AppConnectionState.Offline
        }
    }

    private fun hasInternet(): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
