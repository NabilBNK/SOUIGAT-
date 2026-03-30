package com.souigat.mobile.ui.screens.profile

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.TicketPreviewSettingsStore
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

@Stable
data class ProfileUiState(
    val fullName: String = "Conducteur",
    val roleLabel: String = "Conducteur",
    val pendingCount: Int = 0,
    val syncedCount: Int = 0,
    val quarantinedCount: Int = 0,
    val lastSyncLabel: String = "Pas encore synchronise",
    val ticketPreviewEnabled: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncQueueDao: SyncQueueDao,
    private val syncScheduler: SyncScheduler,
    private val ticketPreviewSettingsStore: TicketPreviewSettingsStore,
    tokenManager: TokenManager
) : ViewModel() {

    val uiState = combine(
        combine(
            syncQueueDao.observePendingCount(),
            syncQueueDao.observeSyncedCount(),
            syncQueueDao.observeQuarantinedCount(),
            syncQueueDao.observeLastSyncedAt(),
            tokenManager.session,
        ) { pendingCount, syncedCount, quarantinedCount, lastSyncedAt, session ->
            ProfileUiState(
                fullName = session.fullName?.ifBlank { null } ?: "Conducteur",
                roleLabel = session.userRole.toRoleLabel(),
                pendingCount = pendingCount,
                syncedCount = syncedCount,
                quarantinedCount = quarantinedCount,
                lastSyncLabel = lastSyncedAt?.toDisplayDateTime() ?: "Pas encore synchronise",
            )
        },
        ticketPreviewSettingsStore.enabled,
    ) { baseState, ticketPreviewEnabled ->
        baseState.copy(
            ticketPreviewEnabled = ticketPreviewEnabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState()
    )

    fun triggerSync() {
        viewModelScope.launch {
            syncQueueDao.retryAllOperationalQuarantined()
            syncQueueDao.retryRecoverableQuarantined()
            syncQueueDao.expediteFailed()
            syncScheduler.triggerOneTimeSync()
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onComplete()
        }
    }

    fun setTicketPreviewEnabled(enabled: Boolean) {
        ticketPreviewSettingsStore.setEnabled(enabled)
    }

    private fun String?.toRoleLabel(): String = when (this) {
        "admin" -> "Administrateur"
        "office_staff" -> "Agent de bureau"
        else -> "Conducteur"
    }
}
