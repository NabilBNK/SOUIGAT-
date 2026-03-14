package com.souigat.mobile.ui.screens.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.entity.CargoTicketEntity
import com.souigat.mobile.data.local.entity.ExpenseEntity
import com.souigat.mobile.data.local.entity.PassengerTicketEntity
import com.souigat.mobile.data.remote.dto.TripDetailDto
import com.souigat.mobile.domain.repository.ExpenseRepository
import com.souigat.mobile.domain.repository.TicketRepository
import com.souigat.mobile.domain.repository.TripException
import com.souigat.mobile.domain.repository.TripRepository
import com.souigat.mobile.util.formatCurrency
import com.souigat.mobile.util.toDisplayDateTime
import com.souigat.mobile.util.toDisplayDateTimeOrSelf
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripPriceLineUiModel(
    val label: String,
    val value: String
)

enum class OfflineActivityKind {
    Passenger,
    Cargo,
    Expense
}

data class OfflineActivityUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val meta: String,
    val amountLabel: String,
    val kind: OfflineActivityKind
)

data class TripDetailUiModel(
    val id: Int,
    val origin: String,
    val destination: String,
    val status: String,
    val statusLabel: String,
    val departureLabel: String,
    val arrivalLabel: String?,
    val conductorName: String,
    val busPlate: String,
    val currency: String,
    val passengerPriceCentimes: Long,
    val cargoSmallPriceCentimes: Long,
    val cargoMediumPriceCentimes: Long,
    val cargoLargePriceCentimes: Long,
    val priceLines: List<TripPriceLineUiModel>,
    val canStartTrip: Boolean,
    val canCompleteTrip: Boolean,
    val canCreateOfflineItems: Boolean
)

sealed class TripDetailUiState {
    object Loading : TripDetailUiState()
    data class Success(val trip: TripDetailUiModel) : TripDetailUiState()
    data class Error(val message: String) : TripDetailUiState()
}

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    expenseRepository: ExpenseRepository,
    ticketRepository: TicketRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tripId: Int = checkNotNull(savedStateHandle["tripId"])

    private val _uiState = MutableStateFlow<TripDetailUiState>(TripDetailUiState.Loading)
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<ActionState>(ActionState.Idle)
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    val passengerTickets = ticketRepository.observePassengerTickets(tripId.toLong())
        .map { tickets -> tickets.map { it.toOfflinePassengerUiModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cargoTickets = ticketRepository.observeCargoTickets(tripId.toLong())
        .map { tickets -> tickets.map { it.toOfflineCargoUiModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expenses = expenseRepository.observeExpensesByTrip(tripId.toLong())
        .map { expenses -> expenses.map { it.toOfflineExpenseUiModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                    _uiState.value = TripDetailUiState.Success(trip.toTripDetailUiModel())
                }
                .onFailure { error ->
                    val exception = error as? TripException ?: TripException.ServerError(500)
                    _uiState.value = TripDetailUiState.Error(exception.toUserMessage())
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
                    loadTripDetail()
                }
                .onFailure { error ->
                    val exception = error as? TripException
                    _actionState.value = ActionState.Error(
                        when (exception) {
                            TripException.NotAssigned -> "Vous n'etes pas le conducteur assigne a ce trajet."
                            is TripException.InvalidStatus -> "Action impossible : ${exception.message}"
                            TripException.NetworkUnavailable -> "Hors ligne. Verifiez votre connexion."
                            else -> "Erreur inconnue."
                        }
                    )
                }
        }
    }

    fun resetActionState() {
        _actionState.value = ActionState.Idle
    }

    private fun TripDetailDto.toTripDetailUiModel(): TripDetailUiModel {
        val statusLabel = when (status) {
            "scheduled" -> "Planifie"
            "in_progress" -> "En cours"
            "completed" -> "Termine"
            "cancelled" -> "Annule"
            else -> status
        }

        return TripDetailUiModel(
            id = id,
            origin = originName,
            destination = destinationName,
            status = status,
            statusLabel = statusLabel,
            departureLabel = departureDatetime.toDisplayDateTimeOrSelf(),
            arrivalLabel = arrivalDatetime?.toDisplayDateTimeOrSelf(),
            conductorName = conductorName,
            busPlate = busPlate,
            currency = currency,
            passengerPriceCentimes = passengerBasePrice,
            cargoSmallPriceCentimes = cargoSmallPrice,
            cargoMediumPriceCentimes = cargoMediumPrice,
            cargoLargePriceCentimes = cargoLargePrice,
            priceLines = listOf(
                TripPriceLineUiModel("Passager", formatCurrency(passengerBasePrice, currency)),
                TripPriceLineUiModel("Petit colis", formatCurrency(cargoSmallPrice, currency)),
                TripPriceLineUiModel("Colis moyen", formatCurrency(cargoMediumPrice, currency)),
                TripPriceLineUiModel("Grand colis", formatCurrency(cargoLargePrice, currency))
            ),
            canStartTrip = status == "scheduled",
            canCompleteTrip = status == "in_progress",
            canCreateOfflineItems = status == "in_progress"
        )
    }

    private fun PassengerTicketEntity.toOfflinePassengerUiModel(): OfflineActivityUiModel {
        return OfflineActivityUiModel(
            id = "passenger_$id",
            title = ticketNumber,
            subtitle = passengerName.ifBlank { "Passager" },
            meta = listOfNotNull(
                seatNumber.ifBlank { null }?.let { "Siege $it" }
            ).ifEmpty { listOf("Billet passager") }.joinToString(" • "),
            amountLabel = formatCurrency(price, currency),
            kind = OfflineActivityKind.Passenger
        )
    }

    private fun CargoTicketEntity.toOfflineCargoUiModel(): OfflineActivityUiModel {
        return OfflineActivityUiModel(
            id = "cargo_$id",
            title = ticketNumber,
            subtitle = "$senderName -> $receiverName",
            meta = when (cargoTier) {
                "small" -> "Petit colis"
                "medium" -> "Colis moyen"
                "large" -> "Grand colis"
                else -> cargoTier
            },
            amountLabel = formatCurrency(price, currency),
            kind = OfflineActivityKind.Cargo
        )
    }

    private fun ExpenseEntity.toOfflineExpenseUiModel(): OfflineActivityUiModel {
        return OfflineActivityUiModel(
            id = "expense_$id",
            title = when (category) {
                "fuel" -> "Carburant"
                "food" -> "Repas"
                "maintenance" -> "Maintenance"
                "tolls" -> "Peage"
                else -> "Depense"
            },
            subtitle = description.ifBlank { "Depense du trajet" },
            meta = createdAt.toDisplayDateTime(),
            amountLabel = formatCurrency(amount, currency),
            kind = OfflineActivityKind.Expense
        )
    }

    private fun TripException.toUserMessage(): String = when (this) {
        TripException.NetworkUnavailable -> "Hors ligne. Verifiez votre connexion."
        TripException.Unauthenticated -> "Session expiree. Veuillez vous reconnecter."
        TripException.NotAssigned -> "Vous n'avez pas acces a ce trajet."
        is TripException.InvalidStatus -> message
        is TripException.DeserializationError -> "Erreur de donnees. Mettez l'application a jour."
        is TripException.ServerError -> if (code == 404) {
            "Ce trajet n'existe plus."
        } else {
            "Erreur serveur $code. Reessayez."
        }
    }
}
