package com.souigat.mobile.ui.screens.tickets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.domain.repository.TicketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CreateTicketUiState {
    object Idle : CreateTicketUiState()
    object Loading : CreateTicketUiState()
    data class Success(val message: String) : CreateTicketUiState()
    data class Error(val message: String) : CreateTicketUiState()
}

@HiltViewModel
class CreateTicketViewModel @Inject constructor(
    private val ticketRepository: TicketRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val tripId: Long = checkNotNull(savedStateHandle.get<Int>("tripId")).toLong()
    
    val passPriceStr: String = savedStateHandle.get<String>("passPrice") ?: "0"
    val smallPriceStr: String = savedStateHandle.get<String>("smallPrice") ?: "0"
    val medPriceStr: String = savedStateHandle.get<String>("medPrice") ?: "0"
    val largePriceStr: String = savedStateHandle.get<String>("largePrice") ?: "0"
    val currency: String = savedStateHandle.get<String>("currency") ?: "DZD"

    private val passPriceCentimes = (passPriceStr.toDoubleOrNull()?.times(100))?.toLong() ?: 0L
    private val smallPriceCentimes = (smallPriceStr.toDoubleOrNull()?.times(100))?.toLong() ?: 0L
    private val medPriceCentimes = (medPriceStr.toDoubleOrNull()?.times(100))?.toLong() ?: 0L
    private val largePriceCentimes = (largePriceStr.toDoubleOrNull()?.times(100))?.toLong() ?: 0L

    private val _uiState = MutableStateFlow<CreateTicketUiState>(CreateTicketUiState.Idle)
    val uiState: StateFlow<CreateTicketUiState> = _uiState.asStateFlow()

    fun createPassengerTicket(
        passengerName: String,
        paymentSource: String, // "cash" or "prepaid"
        seatNumber: String
    ) {
        if (passengerName.isBlank()) {
            _uiState.value = CreateTicketUiState.Error("Le nom du passager est requis.")
            return
        }

        viewModelScope.launch {
            _uiState.value = CreateTicketUiState.Loading
            ticketRepository.createPassengerTicket(
                tripId = tripId,
                passengerName = passengerName,
                price = passPriceCentimes,
                currency = currency,
                paymentSource = paymentSource,
                seatNumber = seatNumber
            ).onSuccess { ticket ->
                _uiState.value = CreateTicketUiState.Success("Billet ${ticket.ticketNumber} créé hors ligne avec succès.")
            }.onFailure {
                _uiState.value = CreateTicketUiState.Error("Erreur lors de la création du billet.")
            }
        }
    }

    fun createCargoTicket(
        senderName: String,
        senderPhone: String,
        receiverName: String,
        receiverPhone: String,
        cargoTier: String, // "small", "medium", "large"
        description: String,
        paymentSource: String // "prepaid" or "pay_on_delivery"
    ) {
        if (senderName.isBlank() || receiverName.isBlank()) {
            _uiState.value = CreateTicketUiState.Error("Les noms de l'expéditeur et du destinataire sont requis.")
            return
        }

        val priceCentimes = when (cargoTier) {
            "small" -> smallPriceCentimes
            "medium" -> medPriceCentimes
            "large" -> largePriceCentimes
            else -> 0L
        }

        viewModelScope.launch {
            _uiState.value = CreateTicketUiState.Loading
            ticketRepository.createCargoTicket(
                tripId = tripId,
                senderName = senderName,
                senderPhone = senderPhone,
                receiverName = receiverName,
                receiverPhone = receiverPhone,
                cargoTier = cargoTier,
                description = description,
                price = priceCentimes,
                currency = currency,
                paymentSource = paymentSource
            ).onSuccess { ticket ->
                _uiState.value = CreateTicketUiState.Success("Billet Colis ${ticket.ticketNumber} créé hors ligne avec succès.")
            }.onFailure {
                _uiState.value = CreateTicketUiState.Error("Erreur lors de la création du billet colis.")
            }
        }
    }
    
    fun resetState() {
        _uiState.value = CreateTicketUiState.Idle
    }
}
