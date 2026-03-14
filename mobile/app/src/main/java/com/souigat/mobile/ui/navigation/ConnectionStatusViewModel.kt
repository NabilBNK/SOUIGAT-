package com.souigat.mobile.ui.navigation

import androidx.lifecycle.ViewModel
import com.souigat.mobile.data.connectivity.BackendConnectionMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ConnectionStatusViewModel @Inject constructor(
    backendConnectionMonitor: BackendConnectionMonitor
) : ViewModel() {
    val connectionState = backendConnectionMonitor.state
}
