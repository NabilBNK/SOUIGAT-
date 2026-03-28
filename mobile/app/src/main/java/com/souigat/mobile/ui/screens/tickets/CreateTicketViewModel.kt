package com.souigat.mobile.ui.screens.tickets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.FormDraftStore
import com.souigat.mobile.data.local.TicketFormDraft
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.entity.TripEntity
import com.souigat.mobile.domain.repository.TicketRepository
import com.souigat.mobile.ui.model.CargoTierPriceUiModel
import com.souigat.mobile.ui.model.TripFormHeaderUiModel
import com.souigat.mobile.util.formatCurrency
import com.souigat.mobile.util.parseCurrencyInput
import com.souigat.mobile.util.toRouteDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class CreateTicketUiState {
    object Idle : CreateTicketUiState()
    object Loading : CreateTicketUiState()
    data class Success(val message: String) : CreateTicketUiState()
    data class Error(val message: String) : CreateTicketUiState()
}

sealed class TicketFormHeaderState {
    object Loading : TicketFormHeaderState()
    data class Ready(
        val header: TripFormHeaderUiModel,
        val passengerBasePriceCentimes: Long,
        val cargoTierPrices: List<CargoTierPriceUiModel>
    ) : TicketFormHeaderState()

    data class Error(val message: String) : TicketFormHeaderState()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CreateTicketViewModel @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val formDraftStore: FormDraftStore,
    tripDao: TripDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val tripId: Long = savedStateHandle.get<Any>("tripId")?.toString()?.toLong()
        ?: error("tripId required")

    private val lookupRequests = MutableStateFlow(0)

    val formState = lookupRequests.flatMapLatest {
        tripDao.observeByLocalOrServerId(tripId)
    }
        .map { trip ->
            if (trip == null) {
                TicketFormHeaderState.Error(
                    "Trajet introuvable localement. Retournez a la liste et rafraichissez."
                )
            } else {
                trip.toTicketFormHeaderState()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TicketFormHeaderState.Loading
        )

    private val _uiState = MutableStateFlow<CreateTicketUiState>(CreateTicketUiState.Idle)
    val uiState: StateFlow<CreateTicketUiState> = _uiState.asStateFlow()

    private val _draftState = MutableStateFlow(formDraftStore.getTicketDraft(tripId))
    val draftState: StateFlow<TicketFormDraft> = _draftState.asStateFlow()

    fun createPassengerTicketBatch(
        count: Int,
        manualPriceInput: String,
        paymentSource: String,
        seatNumber: String,
        boardingPoint: String,
        alightingPoint: String
    ) {
        val form = formState.value as? TicketFormHeaderState.Ready
            ?: run {
                _uiState.value = CreateTicketUiState.Error(
                    "Le trajet n'est pas disponible localement. Reessayez apres rafraichissement."
                )
                return
            }

        val boarding = boardingPoint.trim()
        val alighting = alightingPoint.trim()

        if (boarding.isNotBlank() && alighting.isNotBlank() && boarding.equals(alighting, ignoreCase = true)) {
            _uiState.value = CreateTicketUiState.Error("Le point de montee doit etre different du point de descente.")
            return
        }
        val parsedPrice = parseCurrencyInput(manualPriceInput)
        if (parsedPrice == null || parsedPrice < 100L) {
            _uiState.value = CreateTicketUiState.Error("Veuillez entrer un prix valide.")
            return
        }
        if (paymentSource !in setOf("cash", "prepaid")) {
            _uiState.value = CreateTicketUiState.Error("Mode de paiement invalide.")
            return
        }
        if (count !in 1..50) {
            _uiState.value = CreateTicketUiState.Error("Le nombre de billets doit etre entre 1 et 50.")
            return
        }

        viewModelScope.launch {
            _uiState.value = CreateTicketUiState.Loading
            ticketRepository.createPassengerTicketBatch(
                tripId = tripId,
                count = count,
                price = parsedPrice,
                currency = form.header.currency,
                paymentSource = paymentSource,
                seatNumber = seatNumber.trim(),
                boardingPoint = boarding,
                alightingPoint = alighting
            ).onSuccess { savedCount ->
                clearDraft()
                _uiState.value = CreateTicketUiState.Success(
                    "$savedCount billet(s) crees hors ligne avec succes."
                )
            }.onFailure { error ->
                _uiState.value = CreateTicketUiState.Error(
                    error.message?.takeIf { it.isNotBlank() } ?: "Erreur lors de la creation des billets."
                )
            }
        }
    }

    fun createCargoTicket(
        senderName: String,
        senderPhone: String,
        receiverName: String,
        receiverPhone: String,
        cargoTier: String,
        description: String,
        paymentSource: String
    ) {
        val form = formState.value as? TicketFormHeaderState.Ready
            ?: run {
                _uiState.value = CreateTicketUiState.Error(
                    "Le trajet n'est pas disponible localement. Reessayez apres rafraichissement."
                )
                return
            }

        val sender = senderName.trim()
        val receiver = receiverName.trim()
        val senderPhoneClean = senderPhone.trim()
        val receiverPhoneClean = receiverPhone.trim()
        val descriptionClean = description.trim()

        if (sender.isBlank() || receiver.isBlank()) {
            _uiState.value = CreateTicketUiState.Error(
                "Les noms de l'expediteur et du destinataire sont requis."
            )
            return
        }
        if (paymentSource !in setOf("prepaid", "pay_on_delivery")) {
            _uiState.value = CreateTicketUiState.Error("Mode de paiement invalide.")
            return
        }

        val price = form.cargoTierPrices.firstOrNull { it.tier == cargoTier }?.valueCentimes ?: 0L
        if (price <= 0) {
            _uiState.value = CreateTicketUiState.Error("Prix colis invalide.")
            return
        }

        viewModelScope.launch {
            _uiState.value = CreateTicketUiState.Loading
            ticketRepository.createCargoTicket(
                tripId = tripId,
                senderName = sender,
                senderPhone = senderPhoneClean,
                receiverName = receiver,
                receiverPhone = receiverPhoneClean,
                cargoTier = cargoTier,
                description = descriptionClean,
                price = price,
                currency = form.header.currency,
                paymentSource = paymentSource
            ).onSuccess { ticket ->
                clearDraft()
                _uiState.value = CreateTicketUiState.Success(
                    "Billet colis ${ticket.ticketNumber} cree hors ligne avec succes."
                )
            }.onFailure { error ->
                _uiState.value = CreateTicketUiState.Error(
                    error.message?.takeIf { it.isNotBlank() } ?: "Erreur lors de la creation du billet colis."
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = CreateTicketUiState.Idle
    }

    fun retryLookup() {
        lookupRequests.value += 1
    }

    fun persistDraft(draft: TicketFormDraft) {
        _draftState.value = draft
        formDraftStore.saveTicketDraft(tripId, draft)
    }

    private fun clearDraft() {
        val cleared = TicketFormDraft()
        _draftState.value = cleared
        formDraftStore.clearTicketDraft(tripId)
    }

    private fun TripEntity.toTicketFormHeaderState(): TicketFormHeaderState.Ready {
        val header = TripFormHeaderUiModel(
            tripId = id,
            origin = originOffice,
            destination = destinationOffice,
            busPlate = busPlate,
            departureLabel = departureDateTime.toRouteDateTime(),
            currency = currency,
            statusLabel = when (status) {
                "in_progress" -> "En cours"
                "scheduled" -> "Planifie"
                "completed" -> "Termine"
                "cancelled" -> "Annule"
                else -> status
            }
        )

        val cargoOptions = listOf(
            CargoTierPriceUiModel("small", "Petit colis", cargoSmallPrice),
            CargoTierPriceUiModel("medium", "Colis moyen", cargoMediumPrice),
            CargoTierPriceUiModel("large", "Grand colis", cargoLargePrice)
        ).filter { it.valueCentimes > 0 }
            .map { option ->
                option.copy(label = "${option.label} - ${formatCurrency(option.valueCentimes, currency)}")
            }

        return TicketFormHeaderState.Ready(
            header = header,
            passengerBasePriceCentimes = passengerBasePrice,
            cargoTierPrices = cargoOptions
        )
    }
}
