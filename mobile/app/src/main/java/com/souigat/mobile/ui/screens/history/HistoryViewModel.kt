package com.souigat.mobile.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.entity.TripEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

sealed class HistoryUiState {
    object Loading : HistoryUiState()
    data class Success(val trips: List<TripEntity>) : HistoryUiState()
    object Empty : HistoryUiState()
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    tripDao: TripDao
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = tripDao
        .observeByStatus(listOf("completed", "cancelled"))
        .map { trips ->
            if (trips.isEmpty()) HistoryUiState.Empty
            else HistoryUiState.Success(trips)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState.Loading
        )
}
