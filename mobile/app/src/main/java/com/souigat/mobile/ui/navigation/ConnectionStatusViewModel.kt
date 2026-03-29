package com.souigat.mobile.ui.navigation

import androidx.lifecycle.ViewModel
import com.souigat.mobile.data.connectivity.AppConnectionMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ConnectionStatusViewModel @Inject constructor(
    appConnectionMonitor: AppConnectionMonitor
) : ViewModel() {
    val connectionState = appConnectionMonitor.state
}
