package com.souigat.mobile.ui.screens.trips

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.dao.OperationalTripRow
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.domain.repository.TripException
import com.souigat.mobile.domain.repository.TripRepository
import com.souigat.mobile.util.Constants
import com.souigat.mobile.util.formatCurrency
import com.souigat.mobile.util.isOlderThan
import com.souigat.mobile.util.toRouteDateTime
import com.souigat.mobile.worker.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

enum class TripCardStatusTone {
    Scheduled,
    InProgress,
    Completed,
    Cancelled,
    Unknown
}

@Immutable
data class TripListItemUiModel(
    val id: Long,
    val origin: String,
    val destination: String,
    val busPlate: String,
    val departureLabel: String,
    val statusLabel: String,
    val priceLabel: String,
    val statusTone: TripCardStatusTone
)

@Stable
data class TripListUiState(
    val trips: List<TripListItemUiModel> = emptyList(),
    val isRefreshing: Boolean = false,
    val isInitialLoading: Boolean = true,
    val errorMessage: String? = null,
    val lastRefreshAt: Long? = null
) {
    val hasContent: Boolean get() = trips.isNotEmpty()
    val isStale: Boolean get() = lastRefreshAt?.isOlderThan(Constants.TRIP_LIST_STALE_MS) ?: true
}

private data class TripListRefreshState(
    val isRefreshing: Boolean = false,
    val lastRefreshAt: Long? = null,
    val errorMessage: String? = null,
    val hasAttemptedRefresh: Boolean = false
)

@HiltViewModel
class TripListViewModel @Inject constructor(
    tripDao: TripDao,
    private val tripRepository: TripRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val refreshState = MutableStateFlow(TripListRefreshState())

    val uiState = combine(
        tripDao.observeOperationalSummaries(),
        tripDao.observeLatestOperationalUpdateAt(),
        refreshState
    ) { trips, latestLocalUpdateAt, refresh ->
        val effectiveLastRefreshAt = maxOf(
            refresh.lastRefreshAt ?: 0L,
            latestLocalUpdateAt ?: 0L,
        ).takeIf { it > 0L }

        TripListUiState(
            trips = buildList(trips.size) {
                trips.forEach { trip ->
                    add(trip.toTripListItemUiModel())
                }
            },
            isRefreshing = refresh.isRefreshing,
            isInitialLoading = trips.isEmpty() && refresh.isRefreshing && !refresh.hasAttemptedRefresh,
            errorMessage = refresh.errorMessage,
            lastRefreshAt = effectiveLastRefreshAt,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TripListUiState(isInitialLoading = true)
    )

    init {
        syncScheduler.triggerOneTimeSync()
        viewModelScope.launch {
            tripRepository.startRealtimeTripSync()
                .onFailure { error ->
                    Timber.w(error, "TripListViewModel: realtime Firestore listener not started.")
                }
        }
        refreshTrips(force = true)
    }

    fun onScreenVisible() {
        syncScheduler.triggerOneTimeSync()
        refreshTrips(force = false)
    }

    fun refreshTrips(force: Boolean = true) {
        val snapshot = refreshState.value
        if (snapshot.isRefreshing) {
            return
        }
        if (!force && snapshot.lastRefreshAt != null && !snapshot.lastRefreshAt.isOlderThan(Constants.TRIP_LIST_STALE_MS)) {
            return
        }

        viewModelScope.launch {
            refreshState.update {
                it.copy(
                    isRefreshing = true,
                    errorMessage = null
                )
            }

            tripRepository.getTripList()
                .onSuccess { trips ->
                    Timber.d("refreshTrips: persisted %d trip(s)", trips.size)
                    refreshState.update {
                        it.copy(
                            isRefreshing = false,
                            lastRefreshAt = System.currentTimeMillis(),
                            errorMessage = null,
                            hasAttemptedRefresh = true
                        )
                    }
                }
                .onFailure { error ->
                    val exception = error as? TripException ?: TripException.ServerError(500)
                    Timber.e(error, "refreshTrips failed with %s", exception::class.simpleName)
                    refreshState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = exception.toUserMessage(),
                            hasAttemptedRefresh = true
                        )
                    }
                }
        }
    }

    fun clearError() {
        refreshState.update { it.copy(errorMessage = null) }
    }

    private fun OperationalTripRow.toTripListItemUiModel(): TripListItemUiModel {
        return TripListItemUiModel(
            id = tripId,
            origin = originOffice,
            destination = destinationOffice,
            busPlate = busPlate,
            departureLabel = departureDateTime.toRouteDateTime(),
            statusLabel = status.toFrenchStatus(),
            priceLabel = formatCurrency(passengerBasePrice, currency),
            statusTone = status.toTripCardStatusTone()
        )
    }

    private fun String.toFrenchStatus(): String = when (this) {
        "scheduled" -> "Planifie"
        "in_progress" -> "En cours"
        "completed" -> "Termine"
        "cancelled" -> "Annule"
        else -> this
    }

    private fun String.toTripCardStatusTone(): TripCardStatusTone = when (this) {
        "scheduled" -> TripCardStatusTone.Scheduled
        "in_progress" -> TripCardStatusTone.InProgress
        "completed" -> TripCardStatusTone.Completed
        "cancelled" -> TripCardStatusTone.Cancelled
        else -> TripCardStatusTone.Unknown
    }

    private fun TripException.toUserMessage(): String = when (this) {
        TripException.NetworkUnavailable -> "Hors ligne. Vos trajets locaux restent disponibles."
        TripException.Unauthenticated -> "Session expiree. Reconnectez-vous pour rafraichir."
        TripException.NotAssigned -> "Aucun trajet actif n'est assigne a votre compte."
        is TripException.InvalidStatus -> message
        is TripException.DeserializationError -> "Erreur de donnees. Une mise a jour peut etre necessaire."
        is TripException.ServerError -> "Erreur serveur $code. Reessayez plus tard."
    }
}
