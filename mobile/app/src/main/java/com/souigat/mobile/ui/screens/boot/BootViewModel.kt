package com.souigat.mobile.ui.screens.boot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.firebase.FirebaseSessionManager
import com.souigat.mobile.data.local.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class BootState {
    object Loading : BootState()
    object RequireLogin : BootState()
    data class Authenticated(val role: String) : BootState()
}

@HiltViewModel
class BootViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val firebaseSessionManager: FirebaseSessionManager,
) : ViewModel() {

    private val _bootState = MutableStateFlow<BootState>(BootState.Loading)
    val bootState: StateFlow<BootState> = _bootState.asStateFlow()

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch(Dispatchers.Default) {
            tokenManager.ensureSessionLoaded()
            if (!firebaseSessionManager.hasActiveFirebaseUser()) {
                _bootState.value = BootState.RequireLogin
                return@launch
            }

            val firebaseReady = firebaseSessionManager.ensureSignedIn(forceRefresh = false)
            if (!firebaseReady) {
                _bootState.value = BootState.RequireLogin
                return@launch
            }

            val role = tokenManager.getUserRole() ?: "conductor"
            Timber.i("Boot: Firebase session found. Logging in as $role.")
            _bootState.value = BootState.Authenticated(role)
        }
    }
}
