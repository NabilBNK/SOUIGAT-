package com.souigat.mobile.ui.screens.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.remote.dto.TripListDto
import com.souigat.mobile.domain.repository.TripException
import com.souigat.mobile.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class TripListUiState {
    object Loading : TripListUiState()
    data class Success(val trips: List<TripListDto>) : TripListUiState()
    data class Error(val error: TripException) : TripListUiState()
}

@HiltViewModel
class TripListViewModel @Inject constructor(
    private val tripRepository: TripRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TripListUiState>(TripListUiState.Loading)
    val uiState: StateFlow<TripListUiState> = _uiState.asStateFlow()

    init {
        loadTrips()
    }

    fun loadTrips() {
        viewModelScope.launch {
            _uiState.value = TripListUiState.Loading
            tripRepository.getTripList()
                .onSuccess { trips ->
                    Timber.d("loadTrips: success — ${trips.size} trips")
                    _uiState.value = TripListUiState.Success(trips)
                }
                .onFailure { e ->
                    val exception = e as? TripException ?: TripException.ServerError(500)
                    Timber.e(e, "loadTrips: failed with ${exception::class.simpleName}")
                    _uiState.value = TripListUiState.Error(exception)
                }
        }
    }
}
