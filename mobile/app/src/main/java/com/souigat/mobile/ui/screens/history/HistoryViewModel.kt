package com.souigat.mobile.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.entity.TripEntity
import com.souigat.mobile.util.formatCurrency
import com.souigat.mobile.util.toDisplayDate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryTripUiModel(
    val id: Long,
    val origin: String,
    val destination: String,
    val dateLabel: String,
    val busPlate: String,
    val statusLabel: String,
    val fareLabel: String,
    val isCancelled: Boolean
)

sealed class HistoryUiState {
    object Loading : HistoryUiState()
    data class Success(val trips: List<HistoryTripUiModel>) : HistoryUiState()
    object Empty : HistoryUiState()
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    tripDao: TripDao
) : ViewModel() {

    val uiState = tripDao.observeByStatus(listOf("completed", "cancelled"))
        .map { trips ->
            if (trips.isEmpty()) {
                HistoryUiState.Empty
            } else {
                HistoryUiState.Success(trips.map { it.toHistoryUiModel() })
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState.Loading
        )

    private fun TripEntity.toHistoryUiModel(): HistoryTripUiModel {
        return HistoryTripUiModel(
            id = serverId ?: id,
            origin = originOffice,
            destination = destinationOffice,
            dateLabel = departureDateTime.toDisplayDate(),
            busPlate = busPlate,
            statusLabel = when (status) {
                "completed" -> "Termine"
                "cancelled" -> "Annule"
                else -> status
            },
            fareLabel = formatCurrency(passengerBasePrice, currency),
            isCancelled = status == "cancelled"
        )
    }
}
