package com.souigat.mobile.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.local.dao.SyncQueueDao
import com.souigat.mobile.domain.repository.AuthRepository
import com.souigat.mobile.util.toDisplayDateTime
import com.souigat.mobile.worker.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val fullName: String = "Conducteur",
    val roleLabel: String = "Conducteur",
    val pendingCount: Int = 0,
    val syncedCount: Int = 0,
    val quarantinedCount: Int = 0,
    val lastSyncLabel: String = "Pas encore synchronise"
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    syncQueueDao: SyncQueueDao,
    private val syncScheduler: SyncScheduler,
    private val tokenManager: TokenManager
) : ViewModel() {

    val uiState = combine(
        syncQueueDao.observePendingCount(),
        syncQueueDao.observeSyncedCount(),
        syncQueueDao.observeQuarantinedCount(),
        syncQueueDao.observeLastSyncedAt()
    ) { pendingCount, syncedCount, quarantinedCount, lastSyncedAt ->
        ProfileUiState(
            fullName = tokenManager.getFullName()?.ifBlank { null } ?: "Conducteur",
            roleLabel = tokenManager.getUserRole().toRoleLabel(),
            pendingCount = pendingCount,
            syncedCount = syncedCount,
            quarantinedCount = quarantinedCount,
            lastSyncLabel = lastSyncedAt?.toDisplayDateTime() ?: "Pas encore synchronise"
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(
            fullName = tokenManager.getFullName()?.ifBlank { null } ?: "Conducteur",
            roleLabel = tokenManager.getUserRole().toRoleLabel()
        )
    )

    fun triggerSync() {
        syncScheduler.triggerOneTimeSync()
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onComplete()
        }
    }

    private fun String?.toRoleLabel(): String = when (this) {
        "admin" -> "Administrateur"
        "office_staff" -> "Agent de bureau"
        else -> "Conducteur"
    }
}
