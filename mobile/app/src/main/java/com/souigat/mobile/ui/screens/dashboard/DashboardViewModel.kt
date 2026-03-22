package com.souigat.mobile.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.local.dao.CargoTicketDao
import com.souigat.mobile.data.local.dao.ExpenseDao
import com.souigat.mobile.data.local.dao.PassengerTicketDao
import com.souigat.mobile.data.local.dao.SyncQueueDao
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.entity.CargoTicketEntity
import com.souigat.mobile.data.local.entity.ExpenseEntity
import com.souigat.mobile.data.local.entity.PassengerTicketEntity
import com.souigat.mobile.data.local.entity.TripEntity
import com.souigat.mobile.util.formatCompact
import com.souigat.mobile.util.formatCurrency
import com.souigat.mobile.util.toDisplayDateTime
import com.souigat.mobile.util.toDisplayTime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

enum class DashboardMetricTone {
    Primary,
    Positive,
    Negative,
    Neutral
}

enum class DashboardActivityKind {
    PassengerTicket,
    CargoTicket,
    Expense
}

@Immutable
data class DashboardRouteUiModel(
    val tripId: Long?,
    val origin: String,
    val destination: String,
    val busPlate: String,
    val departureLabel: String,
    val priceLabel: String,
    val statusLabel: String
)

@Immutable
data class DashboardMetricUiModel(
    val id: String,
    val label: String,
    val value: String,
    val supporting: String? = null,
    val tone: DashboardMetricTone = DashboardMetricTone.Neutral
)

@Immutable
data class DashboardActivityUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val timestampLabel: String,
    val amountLabel: String,
    val kind: DashboardActivityKind,
    val createdAt: Long
)

@Stable
data class DashboardUiState(
    val conductorFirstName: String = "Conducteur",
    val conductorFullName: String = "Conducteur",
    val route: DashboardRouteUiModel? = null,
    val heroMetric: DashboardMetricUiModel = DashboardMetricUiModel(
        id = "revenue",
        label = "Revenus",
        value = formatCurrency(0L)
    ),
    val secondaryMetrics: List<DashboardMetricUiModel> = emptyList(),
    val activity: List<DashboardActivityUiModel> = emptyList(),
    val pendingCount: Int = 0,
    val quarantinedCount: Int = 0,
    val lastSyncLabel: String = "Pas encore synchronise"
)

private data class DashboardTripStats(
    val activeTrip: TripEntity? = null,
    val passengerCount: Int = 0,
    val totalRevenueCentimes: Long = 0L,
    val totalExpensesCentimes: Long = 0L
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    tripDao: TripDao,
    private val ticketDao: PassengerTicketDao,
    private val cargoTicketDao: CargoTicketDao,
    private val expenseDao: ExpenseDao,
    private val syncQueueDao: SyncQueueDao,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val tripStatsFlow: Flow<DashboardTripStats> = tripDao.observeActiveTrip().flatMapLatest { trip ->
        if (trip == null) {
            flowOf(DashboardTripStats())
        } else {
            combine(
                ticketDao.observeActiveCount(trip.id),
                ticketDao.observeTotalRevenue(trip.id),
                expenseDao.observeTotalAmount(trip.id)
            ) { passengerCount, revenue, expenses ->
                DashboardTripStats(
                    activeTrip = trip,
                    passengerCount = passengerCount,
                    totalRevenueCentimes = revenue,
                    totalExpensesCentimes = expenses
                )
            }
        }
    }

    private val activityFeedFlow: Flow<List<DashboardActivityUiModel>> = combine(
        ticketDao.observeRecentGlobal(),
        cargoTicketDao.observeRecentGlobal(),
        expenseDao.observeRecentGlobal(),
        tripDao.observeAll()
    ) { passengerTickets, cargoTickets, expenses, trips ->
        buildActivityFeed(
            tickets = passengerTickets,
            cargoTickets = cargoTickets,
            expenses = expenses,
            routeLabels = trips.associate { trip ->
                trip.id to "${trip.originOffice} -> ${trip.destinationOffice}"
            }
        )
    }

    val uiState = combine(
        tripStatsFlow,
        activityFeedFlow,
        syncQueueDao.observePendingCount(),
        syncQueueDao.observeQuarantinedCount(),
        syncQueueDao.observeLastSyncedAt()
    ) { tripStats, activity, pendingCount, quarantinedCount, lastSyncedAt ->
        val firstName = tokenManager.getFirstName()?.ifBlank { null } ?: "Conducteur"
        val fullName = tokenManager.getFullName()?.ifBlank { null } ?: firstName
        val currency = tripStats.activeTrip?.currency ?: "DZD"

        DashboardUiState(
            conductorFirstName = firstName,
            conductorFullName = fullName,
            route = tripStats.activeTrip?.toDashboardRouteUiModel(),
            heroMetric = DashboardMetricUiModel(
                id = "revenue",
                label = "Revenus",
                value = formatCurrency(tripStats.totalRevenueCentimes, currency),
                supporting = if (tripStats.activeTrip != null) "Trajet en cours" else "Aucun trajet actif",
                tone = DashboardMetricTone.Primary
            ),
            secondaryMetrics = listOf(
                DashboardMetricUiModel(
                    id = "passengers",
                    label = "Passagers",
                    value = tripStats.passengerCount.toString(),
                    supporting = "Billets actifs",
                    tone = DashboardMetricTone.Positive
                ),
                DashboardMetricUiModel(
                    id = "expenses",
                    label = "Depenses",
                    value = formatCurrency(tripStats.totalExpensesCentimes, currency),
                    supporting = "Depuis le depart",
                    tone = DashboardMetricTone.Negative
                ),
                DashboardMetricUiModel(
                    id = "pending",
                    label = "En attente",
                    value = pendingCount.toString(),
                    supporting = "Elements a sync",
                    tone = DashboardMetricTone.Neutral
                ),
                DashboardMetricUiModel(
                    id = "avg_ticket",
                    label = "Ticket moyen",
                    value = if (tripStats.passengerCount > 0) {
                        formatCurrency(tripStats.totalRevenueCentimes / tripStats.passengerCount, currency)
                    } else {
                        formatCurrency(0L, currency)
                    },
                    supporting = tripStats.activeTrip?.busPlate ?: "Pas de bus actif",
                    tone = DashboardMetricTone.Neutral
                )
            ),
            activity = activity,
            pendingCount = pendingCount,
            quarantinedCount = quarantinedCount,
            lastSyncLabel = lastSyncedAt?.toDisplayDateTime() ?: "Pas encore synchronise"
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    private fun TripEntity.toDashboardRouteUiModel(): DashboardRouteUiModel {
        return DashboardRouteUiModel(
            tripId = serverId?.toLongOrNull(),
            origin = originOffice,
            destination = destinationOffice,
            busPlate = busPlate,
            departureLabel = departureDateTime.toDisplayDateTime(),
            priceLabel = formatCompact(passengerBasePrice) + " " + currency,
            statusLabel = "En cours"
        )
    }

    private fun buildActivityFeed(
        tickets: List<PassengerTicketEntity>,
        cargoTickets: List<CargoTicketEntity>,
        expenses: List<ExpenseEntity>,
        routeLabels: Map<Long, String>
    ): List<DashboardActivityUiModel> {
        val ticketItems = tickets.take(4).map { ticket ->
            DashboardActivityUiModel(
                id = "ticket_${ticket.id}",
                title = "Billet ${ticket.ticketNumber}",
                subtitle = buildActivitySubtitle(
                    primary = ticket.passengerName.ifBlank { "Passager" },
                    routeLabel = routeLabels[ticket.tripId]
                ),
                timestampLabel = ticket.createdAt.toDisplayTime(),
                amountLabel = formatCurrency(ticket.price, ticket.currency),
                kind = DashboardActivityKind.PassengerTicket,
                createdAt = ticket.createdAt
            )
        }

        val cargoItems = cargoTickets.take(4).map { cargoTicket ->
            DashboardActivityUiModel(
                id = "cargo_${cargoTicket.id}",
                title = "Colis ${cargoTicket.ticketNumber}",
                subtitle = buildActivitySubtitle(
                    primary = cargoTicket.receiverName.ifBlank { cargoTicket.senderName.ifBlank { "Colis" } },
                    routeLabel = routeLabels[cargoTicket.tripId]
                ),
                timestampLabel = cargoTicket.createdAt.toDisplayTime(),
                amountLabel = formatCurrency(cargoTicket.price, cargoTicket.currency),
                kind = DashboardActivityKind.CargoTicket,
                createdAt = cargoTicket.createdAt
            )
        }

        val expenseItems = expenses.take(4).map { expense ->
            DashboardActivityUiModel(
                id = "expense_${expense.id}",
                title = expense.category.toExpenseTitle(),
                subtitle = buildActivitySubtitle(
                    primary = expense.description.ifBlank { "Depense du trajet" },
                    routeLabel = routeLabels[expense.tripId]
                ),
                timestampLabel = expense.createdAt.toDisplayTime(),
                amountLabel = formatCurrency(expense.amount, expense.currency),
                kind = DashboardActivityKind.Expense,
                createdAt = expense.createdAt
            )
        }

        return (ticketItems + cargoItems + expenseItems)
            .sortedByDescending { it.createdAt }
            .take(6)
    }

    private fun buildActivitySubtitle(primary: String, routeLabel: String?): String {
        return if (routeLabel.isNullOrBlank()) {
            primary
        } else {
            "$primary - $routeLabel"
        }
    }

    private fun String.toExpenseTitle(): String = when (this) {
        "fuel" -> "Carburant"
        "food" -> "Repas"
        "tolls" -> "Peage"
        "maintenance" -> "Maintenance"
        else -> "Autre depense"
    }
}
