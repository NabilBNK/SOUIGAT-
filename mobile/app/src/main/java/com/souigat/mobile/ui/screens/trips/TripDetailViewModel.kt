package com.souigat.mobile.ui.screens.trips

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.local.entity.CargoTicketEntity
import com.souigat.mobile.data.local.entity.ExpenseEntity
import com.souigat.mobile.data.local.entity.PassengerTicketEntity
import com.souigat.mobile.domain.model.TripCompletionRecap
import com.souigat.mobile.domain.model.TripDetail
import com.souigat.mobile.domain.model.TripStatusResult
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

@Immutable
data class TripPriceLineUiModel(
    val label: String,
    val value: String
)

enum class OfflineActivityKind {
    Passenger,
    Cargo,
    Expense
}

@Immutable
data class OfflineActivityUiModel(
    val id: String,
    val localId: Long? = null,
    val title: String,
    val subtitle: String,
    val meta: String,
    val amountLabel: String,
    val kind: OfflineActivityKind,
    val status: String? = null,
    val statusLabel: String? = null,
    val canDeliverToReceiver: Boolean = false,
    val canHandoverToAgency: Boolean = false,
)

enum class CargoDeliveryTarget {
    Receiver,
    Agency,
}

@Stable
data class TripDetailUiModel(
    val id: Long,
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
    val canCreateOfflineItems: Boolean,
    val canCreateCargoItems: Boolean
)

@Immutable
data class SettlementPreviewUiModel(
    val passengerCount: Int,
    val cargoCount: Int,
    val passengerCashLabel: String,
    val cargoCashLabel: String,
    val expensesLabel: String,
    val cashExpectedLabel: String,
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
    private val ticketRepository: TicketRepository,
    private val tokenManager: TokenManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tripId: Long = savedStateHandle.get<Any>("tripId")
        ?.toString()
        ?.toLongOrNull()
        ?: error("tripId argument is required")

    private val _uiState = MutableStateFlow<TripDetailUiState>(TripDetailUiState.Loading)
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<ActionState>(ActionState.Idle)
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    private val _cargoActionState = MutableStateFlow<CargoActionState>(CargoActionState.Idle)
    val cargoActionState: StateFlow<CargoActionState> = _cargoActionState.asStateFlow()

    val passengerTicketCount = ticketRepository.observePassengerTicketCount(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val passengerTickets = ticketRepository.observePassengerTickets(tripId)
        .map { tickets -> tickets.map { it.toOfflinePassengerUiModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cargoTickets = ticketRepository.observeCargoTickets(tripId)
        .map { tickets -> tickets.map { it.toOfflineCargoUiModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expenses = expenseRepository.observeExpensesByTrip(tripId)
        .map { expenses -> expenses.map { it.toOfflineExpenseUiModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    sealed class ActionState {
        object Idle : ActionState()
        object Loading : ActionState()
        data class Error(val message: String) : ActionState()
        data class Success(val response: TripStatusResult) : ActionState()
    }

    sealed class CargoActionState {
        object Idle : CargoActionState()
        object Loading : CargoActionState()
        data class Error(val message: String) : CargoActionState()
        data class Success(val message: String) : CargoActionState()
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
                    viewModelScope.launch {
                        tripRepository.refreshTripActivity(tripId)
                    }
                }
                .onFailure { error ->
                    val exception = error as? TripException ?: TripException.DataError("Erreur inconnue")
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

    private fun executeAction(action: suspend () -> Result<TripStatusResult>) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            action()
                .onSuccess { response ->
                    _actionState.value = ActionState.Success(response)
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

    fun deliverCargo(cargoLocalId: Long, target: CargoDeliveryTarget) {
        viewModelScope.launch {
            _cargoActionState.value = CargoActionState.Loading
            val result = when (target) {
                CargoDeliveryTarget.Receiver -> ticketRepository.deliverCargoToReceiver(cargoLocalId)
                CargoDeliveryTarget.Agency -> ticketRepository.handoverCargoToAgency(cargoLocalId)
            }
            result
                .onSuccess {
                    _cargoActionState.value = CargoActionState.Success(
                        when (target) {
                            CargoDeliveryTarget.Receiver -> "Colis livre au destinataire."
                            CargoDeliveryTarget.Agency -> "Colis transfere a l'agence."
                        }
                    )
                }
                .onFailure { error ->
                    _cargoActionState.value = CargoActionState.Error(
                        error.message?.takeIf { it.isNotBlank() } ?: "Echec de la mise a jour du colis."
                    )
                }
        }
    }

    fun handoverAllCargoToAgency() {
        viewModelScope.launch {
            _cargoActionState.value = CargoActionState.Loading
            ticketRepository.handoverAllCargoToAgency(tripId)
                .onSuccess { movedCount ->
                    _cargoActionState.value = CargoActionState.Success(
                        if (movedCount > 0) {
                            "$movedCount colis transfere(s) a l'agence."
                        } else {
                            "Aucun colis ouvert a transferer."
                        }
                    )
                }
                .onFailure { error ->
                    _cargoActionState.value = CargoActionState.Error(
                        error.message?.takeIf { it.isNotBlank() } ?: "Echec du transfert global des colis."
                    )
                }
        }
    }

    fun resetCargoActionState() {
        _cargoActionState.value = CargoActionState.Idle
    }

    fun toSettlementPreviewUiModel(recap: TripCompletionRecap): SettlementPreviewUiModel {
        return SettlementPreviewUiModel(
            passengerCount = recap.passengerCount,
            cargoCount = recap.cargoCount,
            passengerCashLabel = formatCurrency(recap.passengerCashTotal, recap.currency),
            cargoCashLabel = formatCurrency(recap.cargoCashTotal, recap.currency),
            expensesLabel = formatCurrency(recap.expensesTotal, recap.currency),
            cashExpectedLabel = formatCurrency(recap.cashExpected, recap.currency),
        )
    }

    private fun TripDetail.toTripDetailUiModel(): TripDetailUiModel {
        val userRole = tokenManager.getUserRole().orEmpty()
        val canCreateCargoItems = status in setOf("scheduled", "in_progress") &&
            userRole in setOf("admin", "office_staff", "conductor")

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
            canCreateOfflineItems = status == "in_progress",
            canCreateCargoItems = canCreateCargoItems
        )
    }

    private fun PassengerTicketEntity.toOfflinePassengerUiModel(): OfflineActivityUiModel {
        return OfflineActivityUiModel(
            id = "passenger_$id",
            title = ticketNumber,
            subtitle = passengerName.ifBlank { "Passager" },
            meta = listOfNotNull(
                seatNumber.ifBlank { null }?.let { "Siege $it" }
            ).ifEmpty { listOf("Billet passager") }.joinToString(" - "),
            amountLabel = formatCurrency(price, currency),
            kind = OfflineActivityKind.Passenger
        )
    }

    private fun CargoTicketEntity.toOfflineCargoUiModel(): OfflineActivityUiModel {
        val normalizedStatus = status.trim().lowercase()
        return OfflineActivityUiModel(
            id = "cargo_$id",
            localId = id,
            title = ticketNumber,
            subtitle = "$senderName -> $receiverName",
            meta = when (cargoTier) {
                "small" -> "Petit colis"
                "medium" -> "Colis moyen"
                "large" -> "Grand colis"
                else -> cargoTier
            },
            amountLabel = formatCurrency(price, currency),
            kind = OfflineActivityKind.Cargo,
            status = normalizedStatus,
            statusLabel = cargoStatusLabel(normalizedStatus),
            canDeliverToReceiver = normalizedStatus in setOf("created", "loaded", "in_transit"),
            canHandoverToAgency = normalizedStatus in setOf("created", "loaded", "in_transit"),
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
        is TripException.DataError -> detail
    }

    private fun cargoStatusLabel(status: String): String = when (status) {
        "created" -> "Cree"
        "loaded" -> "Charge"
        "in_transit" -> "En transit"
        "arrived" -> "Arrive agence"
        "delivered" -> "Livre"
        "cancelled" -> "Annule"
        else -> status
    }
}

