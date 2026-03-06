package com.souigat.mobile.ui.screens.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.remote.dto.TripDetailDto
import com.souigat.mobile.domain.repository.TripException
import com.souigat.mobile.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class TripDetailUiState {
    object Loading : TripDetailUiState()
    data class Success(val trip: TripDetailDto) : TripDetailUiState()
    data class Error(val error: TripException) : TripDetailUiState()
}

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tripId: Int = checkNotNull(savedStateHandle["tripId"])

    private val _uiState = MutableStateFlow<TripDetailUiState>(TripDetailUiState.Loading)
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<ActionState>(ActionState.Idle)
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    sealed class ActionState {
        object Idle : ActionState()
        object Loading : ActionState()
        data class Error(val message: String) : ActionState()
        object Success : ActionState()
    }

    init {
        loadTripDetail()
    }

    fun loadTripDetail() {
        viewModelScope.launch {
            _uiState.value = TripDetailUiState.Loading
            tripRepository.getTripDetail(tripId)
                .onSuccess { trip ->
                    _uiState.value = TripDetailUiState.Success(trip)
                }
                .onFailure { e ->
                    val exception = e as? TripException ?: TripException.ServerError(500)
                    _uiState.value = TripDetailUiState.Error(exception)
                }
        }
    }

    fun startTrip() {
        executeAction { tripRepository.startTrip(tripId) }
    }

    fun completeTrip() {
        executeAction { tripRepository.completeTrip(tripId) }
    }

    private fun executeAction(action: suspend () -> Result<Any>) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            action()
                .onSuccess {
                    _actionState.value = ActionState.Success
                    loadTripDetail() // Refresh the detail locally
                }
                .onFailure { e ->
                    val message = when (val ex = e as? TripException) {
                        is TripException.NotAssigned -> "Vous n'êtes pas le conducteur assigné à ce trajet."
                        is TripException.InvalidStatus -> "Action impossible : ${ex.message}"
                        is TripException.NetworkUnavailable -> "Hors ligne. Vérifiez votre connexion."
                        else -> "Erreur inconnue."
                    }
                    _actionState.value = ActionState.Error(message)
                }
        }
    }

    fun resetActionState() {
        _actionState.value = ActionState.Idle
    }
}
