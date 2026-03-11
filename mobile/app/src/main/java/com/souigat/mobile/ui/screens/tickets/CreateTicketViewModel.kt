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

    val tripId: Long = savedStateHandle.get<Any>("tripId")?.toString()?.toLong() ?: error("tripId required")
    
    val passPriceStr: String = savedStateHandle.get<String>("passPrice") ?: "0"
    val smallPriceStr: String = savedStateHandle.get<String>("smallPrice") ?: "0"
    val medPriceStr: String = savedStateHandle.get<String>("medPrice") ?: "0"
    val largePriceStr: String = savedStateHandle.get<String>("largePrice") ?: "0"
    val currency: String = savedStateHandle.get<String>("currency") ?: "DZD"

    private val passPrice = passPriceStr.toLongOrNull() ?: 0L
    private val smallPrice = smallPriceStr.toLongOrNull() ?: 0L
    private val medPrice = medPriceStr.toLongOrNull() ?: 0L
    private val largePrice = largePriceStr.toLongOrNull() ?: 0L

    private val _uiState = MutableStateFlow<CreateTicketUiState>(CreateTicketUiState.Idle)
    val uiState: StateFlow<CreateTicketUiState> = _uiState.asStateFlow()

    fun createPassengerTicketBatch(
        count: Int,
        priceOverrideStr: String,
        paymentSource: String, // "cash" or "prepaid"
        seatNumber: String
    ) {
        val priceOverride = priceOverrideStr.toLongOrNull()
        if (priceOverride == null) {
            _uiState.value = CreateTicketUiState.Error("Prix invalide.")
            return
        }
        
        if (priceOverride < 100) {
            _uiState.value = CreateTicketUiState.Error("Le prix doit être au moins de 100 DA.")
            return
        }

        if (passPrice > 0 && priceOverride > (passPrice * 1.5).toLong()) {
            _uiState.value = CreateTicketUiState.Error("Le prix dépasse la limite maximale autorisée.")
            return
        }

        if (count < 1 || count > 50) {
            _uiState.value = CreateTicketUiState.Error("Le nombre de billets doit être entre 1 et 50.")
            return
        }

        viewModelScope.launch {
            _uiState.value = CreateTicketUiState.Loading
            ticketRepository.createPassengerTicketBatch(
                tripId = tripId,
                count = count,
                price = priceOverride,
                currency = currency,
                paymentSource = paymentSource,
                seatNumber = seatNumber
            ).onSuccess { savedCount ->
                _uiState.value = CreateTicketUiState.Success("$savedCount billet(s) créé(s) hors ligne avec succès.")
            }.onFailure {
                _uiState.value = CreateTicketUiState.Error("Erreur lors de la création des billets.")
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

        val price = when (cargoTier) {
            "small" -> smallPrice
            "medium" -> medPrice
            "large" -> largePrice
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
                price = price,
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
