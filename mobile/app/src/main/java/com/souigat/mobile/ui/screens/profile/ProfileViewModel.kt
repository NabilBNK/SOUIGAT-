package com.souigat.mobile.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.local.dao.SyncQueueDao
import com.souigat.mobile.worker.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val fullName: String = "",
    val role: String = "",
    val pendingCount: Int = 0,
    val syncedCount: Int = 0,
    val quarantinedCount: Int = 0,
    val isSyncTriggered: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val syncQueueDao: SyncQueueDao,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        syncQueueDao.observePendingCount(),
        syncQueueDao.observeSyncedCount(),
        syncQueueDao.observeQuarantinedCount()
    ) { pending, synced, quarantined ->
        ProfileUiState(
            fullName         = tokenManager.getFullName() ?: "—",
            role             = formatRole(tokenManager.getUserRole()),
            pendingCount     = pending,
            syncedCount      = synced,
            quarantinedCount = quarantined
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(
            fullName = tokenManager.getFullName() ?: "—",
            role     = formatRole(tokenManager.getUserRole())
        )
    )

    fun triggerSync() {
        syncScheduler.triggerOneTimeSync()
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            tokenManager.clearAll()
            onDone()
        }
    }

    private fun formatRole(role: String?): String = when (role) {
        "conductor"    -> "Conducteur"
        "office_staff" -> "Personnel bureau"
        "admin"        -> "Administrateur"
        else           -> role ?: "—"
    }
}
