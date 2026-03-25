package com.souigat.mobile.ui.screens.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.dao.HistoryTripRow
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.util.formatCurrency
import com.souigat.mobile.util.toDisplayDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Immutable
data class HistoryTripUiModel(
    val id: Long,
    val monthLabel: String,
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

    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)
    private val deviceZone = ZoneId.systemDefault()

    val uiState = tripDao.observeHistorySummaries()
        .map { trips ->
            if (trips.isEmpty()) {
                HistoryUiState.Empty
            } else {
                HistoryUiState.Success(
                    buildList(trips.size) {
                        trips.forEach { trip ->
                            add(trip.toHistoryUiModel())
                        }
                    }
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState.Loading
        )

    private fun HistoryTripRow.toHistoryUiModel(): HistoryTripUiModel {
        return HistoryTripUiModel(
            id = tripId,
            monthLabel = departureDateTime.toMonthLabel(),
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

    private fun Long.toMonthLabel(): String {
        return Instant.ofEpochMilli(this)
            .atZone(deviceZone)
            .format(monthFormatter)
            .uppercase(Locale.FRANCE)
    }
}
