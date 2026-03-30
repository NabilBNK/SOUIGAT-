package com.souigat.mobile.ui.screens.tickets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.FormDraftStore
import com.souigat.mobile.data.local.TicketFormDraft
import com.souigat.mobile.data.local.TicketPreviewSettingsStore
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.entity.TripEntity
import com.souigat.mobile.domain.model.TripRouteSegmentTariff
import com.souigat.mobile.domain.model.TripRouteStop
import com.souigat.mobile.domain.repository.TicketRepository
import com.souigat.mobile.domain.repository.TripRepository
import com.souigat.mobile.ui.model.CargoTierPriceUiModel
import com.souigat.mobile.ui.model.TripFormHeaderUiModel
import com.souigat.mobile.util.formatCurrency
import com.souigat.mobile.util.toRouteDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
import org.json.JSONArray

sealed class CreateTicketUiState {
    object Idle : CreateTicketUiState()
    object Loading : CreateTicketUiState()
    data class Success(
        val message: String,
        val preview: PassengerTicketPreviewModel? = null,
    ) : CreateTicketUiState()
    data class Error(val message: String) : CreateTicketUiState()
}

data class PassengerTicketPreviewModel(
    val ticketNumber: String,
    val boardingPoint: String,
    val destinationPoint: String,
    val routeLabel: String,
    val priceLabel: String,
    val dateLabel: String,
)

sealed class TicketFormHeaderState {
    object Loading : TicketFormHeaderState()
    data class Ready(
        val header: TripFormHeaderUiModel,
        val passengerBasePriceCentimes: Long,
        val cargoTierPrices: List<CargoTierPriceUiModel>,
        val routeStops: List<String>,
        val routeSegments: List<TripRouteSegmentTariff>,
    ) : TicketFormHeaderState()

    data class Error(val message: String) : TicketFormHeaderState()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CreateTicketViewModel @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val tripRepository: TripRepository,
    private val ticketPreviewSettingsStore: TicketPreviewSettingsStore,
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
    val ticketPreviewEnabled: StateFlow<Boolean> = ticketPreviewSettingsStore.enabled

    init {
        refreshTripDetail()
    }

    fun createPassengerTicketBatch(
        count: Int,
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
        val computedPrice = computeForwardPassengerFare(form, boarding, alighting)
        if (computedPrice == null || computedPrice < 100L) {
            _uiState.value = CreateTicketUiState.Error("Le trajet selectionne est invalide pour ce template.")
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
                price = computedPrice,
                currency = form.header.currency,
                paymentSource = paymentSource,
                seatNumber = seatNumber.trim(),
                boardingPoint = boarding,
                alightingPoint = alighting
            ).onSuccess { ticketNumbers ->
                clearDraft()
                val firstTicketNumber = ticketNumbers.firstOrNull().orEmpty()
                val preview = firstTicketNumber.takeIf { it.isNotBlank() }?.let {
                    PassengerTicketPreviewModel(
                        ticketNumber = it,
                        boardingPoint = boarding,
                        destinationPoint = alighting,
                        routeLabel = form.header.routeTemplateName.ifBlank {
                            "${form.header.origin} - ${form.header.destination}"
                        },
                        priceLabel = formatCurrency(computedPrice, form.header.currency),
                        dateLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")),
                    )
                }
                _uiState.value = CreateTicketUiState.Success(
                    "${ticketNumbers.size} billet(s) crees hors ligne avec succes.",
                    preview = preview,
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
                    "Billet colis ${ticket.ticketNumber} cree hors ligne avec succes.",
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
        refreshTripDetail()
        lookupRequests.value += 1
    }

    private fun refreshTripDetail() {
        viewModelScope.launch {
            tripRepository.getTripDetail(tripId)
        }
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
            routeTemplateName = routeTemplateName,
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
            CargoTierPriceUiModel("small", "Petit", "Petit colis", "", cargoSmallPrice),
            CargoTierPriceUiModel("medium", "Colis moyen", "Colis moyen", "", cargoMediumPrice),
            CargoTierPriceUiModel("large", "Grand", "Grand colis", "", cargoLargePrice)
        ).filter { it.valueCentimes > 0 }
            .map { option ->
                option.copy(
                    label = "${option.label} - ${formatCurrency(option.valueCentimes, currency)}",
                    amountLabel = formatCurrency(option.valueCentimes, currency),
                )
            }

        return TicketFormHeaderState.Ready(
            header = header,
            passengerBasePriceCentimes = passengerBasePrice,
            cargoTierPrices = cargoOptions,
            routeStops = routeStopsForUi(this),
            routeSegments = decodeRouteSegments(routeSegmentTariffSnapshot),
        )
    }

    private fun computeForwardPassengerFare(
        form: TicketFormHeaderState.Ready,
        boardingPoint: String,
        alightingPoint: String,
    ): Long? {
        val normalizedStops = form.routeStops.map { it.trim().lowercase() }
        val boardingIndex = normalizedStops.indexOf(boardingPoint.trim().lowercase())
        val alightingIndex = normalizedStops.indexOf(alightingPoint.trim().lowercase())
        if (boardingIndex == -1 || alightingIndex == -1 || alightingIndex <= boardingIndex) {
            return null
        }

        var total = 0L
        for (index in boardingIndex until alightingIndex) {
            val fromOrder = index + 1
            val toOrder = index + 2
            val segment = form.routeSegments.firstOrNull {
                it.fromStopOrder == fromOrder && it.toStopOrder == toOrder
            } ?: return null
            total += segment.passengerPrice
        }
        return total
    }

    private fun routeStopsForUi(trip: TripEntity): List<String> {
        val decoded = decodeRouteStops(trip.routeStopSnapshot)
            .map { it.officeName.trim() }
            .filter { it.isNotBlank() }
        if (decoded.isNotEmpty()) {
            val merged = mutableListOf<String>()
            fun addDistinct(value: String) {
                if (merged.none { it.equals(value, ignoreCase = true) }) {
                    merged += value
                }
            }

            val origin = trip.originOffice.trim()
            val destination = trip.destinationOffice.trim()
            if (origin.isNotBlank()) {
                addDistinct(origin)
            }
            decoded.forEach(::addDistinct)
            if (destination.isNotBlank()) {
                addDistinct(destination)
            }
            return merged
        }
        return listOf(trip.originOffice, trip.destinationOffice).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    private fun decodeRouteStops(raw: String): List<TripRouteStop> {
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val stopName = item.optString("stop_name", "")
                    val officeName = item.optString("office_name", "")
                    val displayName = stopName.takeIf { it.isNotBlank() } ?: officeName
                    val officeId = item.optInt("office_id", -1)
                    val stopOrder = item.optInt("stop_order", -1)
                    if (displayName.isBlank() || stopOrder < 0) {
                        continue
                    }
                    add(
                        TripRouteStop(
                            officeId = officeId,
                            officeName = displayName,
                            stopOrder = stopOrder,
                        )
                    )
                }
            }.sortedBy { it.stopOrder }
        }.getOrElse { emptyList() }
    }

    private fun decodeRouteSegments(raw: String): List<TripRouteSegmentTariff> {
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val fromStopOrder = item.optInt("from_stop_order", -1)
                    val toStopOrder = item.optInt("to_stop_order", -1)
                    val passengerPrice = item.optLong("passenger_price", -1L)
                    val currency = item.optString("currency", "DZD")
                    if (fromStopOrder < 0 || toStopOrder < 0 || passengerPrice < 0) {
                        continue
                    }
                    add(
                        TripRouteSegmentTariff(
                            fromStopOrder = fromStopOrder,
                            toStopOrder = toStopOrder,
                            passengerPrice = passengerPrice,
                            currency = currency,
                        )
                    )
                }
            }.sortedBy { it.fromStopOrder }
        }.getOrElse { emptyList() }
    }
}
