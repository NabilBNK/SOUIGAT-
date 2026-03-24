package com.souigat.mobile.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.local.dao.CargoDashboardActivityRow
import com.souigat.mobile.data.local.dao.CargoTicketDao
import com.souigat.mobile.data.local.dao.ExpenseDashboardActivityRow
import com.souigat.mobile.data.local.dao.ExpenseDao
import com.souigat.mobile.data.local.dao.PassengerDashboardActivityRow
import com.souigat.mobile.data.local.dao.PassengerTicketDao
import com.souigat.mobile.data.local.dao.SyncQueueDao
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.entity.TripEntity
import com.souigat.mobile.util.formatCompact
import com.souigat.mobile.util.formatCurrency
import com.souigat.mobile.util.toDisplayDateTime
import com.souigat.mobile.util.toDisplayTime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
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
        ticketDao.observeRecentDashboardItems(),
        cargoTicketDao.observeRecentDashboardItems(),
        expenseDao.observeRecentDashboardItems()
    ) { passengerTickets, cargoTickets, expenses ->
        buildActivityFeed(
            tickets = passengerTickets,
            cargoTickets = cargoTickets,
            expenses = expenses
        )
    }.flowOn(Dispatchers.Default)

    private val syncMetaFlow = combine(
        syncQueueDao.observeLastSyncedAt(),
        tokenManager.session
    ) { lastSyncedAt, session ->
        lastSyncedAt to session
    }

    val uiState = combine(
        tripStatsFlow,
        activityFeedFlow,
        syncQueueDao.observePendingCount(),
        syncQueueDao.observeQuarantinedCount(),
        syncMetaFlow
    ) { tripStats, activity, pendingCount, quarantinedCount, syncMeta ->
        val (lastSyncedAt, session) = syncMeta
        val firstName = session.firstName?.ifBlank { null } ?: "Conducteur"
        val fullName = session.fullName?.ifBlank { null } ?: firstName
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
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    private fun TripEntity.toDashboardRouteUiModel(): DashboardRouteUiModel {
        return DashboardRouteUiModel(
            tripId = serverId ?: id,
            origin = originOffice,
            destination = destinationOffice,
            busPlate = busPlate,
            departureLabel = departureDateTime.toDisplayDateTime(),
            priceLabel = formatCompact(passengerBasePrice) + " " + currency,
            statusLabel = "En cours"
        )
    }

    private fun buildActivityFeed(
        tickets: List<PassengerDashboardActivityRow>,
        cargoTickets: List<CargoDashboardActivityRow>,
        expenses: List<ExpenseDashboardActivityRow>
    ): List<DashboardActivityUiModel> {
        if (tickets.isEmpty() && cargoTickets.isEmpty() && expenses.isEmpty()) {
            return emptyList()
        }

        val maxItems = 6
        val activities = ArrayList<DashboardActivityUiModel>(minOf(maxItems, tickets.size + cargoTickets.size + expenses.size))
        var ticketIndex = 0
        var cargoIndex = 0
        var expenseIndex = 0

        while (activities.size < maxItems) {
            val ticket = tickets.getOrNull(ticketIndex)
            val cargoTicket = cargoTickets.getOrNull(cargoIndex)
            val expense = expenses.getOrNull(expenseIndex)

            val nextCreatedAt = maxOf(
                ticket?.createdAt ?: Long.MIN_VALUE,
                cargoTicket?.createdAt ?: Long.MIN_VALUE,
                expense?.createdAt ?: Long.MIN_VALUE
            )

            if (nextCreatedAt == Long.MIN_VALUE) {
                break
            }

            when (nextCreatedAt) {
                ticket?.createdAt -> {
                    activities += ticket.toActivityUiModel()
                    ticketIndex++
                }

                cargoTicket?.createdAt -> {
                    activities += cargoTicket.toActivityUiModel()
                    cargoIndex++
                }

                else -> {
                    activities += expense!!.toActivityUiModel()
                    expenseIndex++
                }
            }
        }

        return activities
    }

    private fun buildActivitySubtitle(primary: String, routeLabel: String?): String {
        return if (routeLabel.isNullOrBlank()) {
            primary
        } else {
            "$primary - $routeLabel"
        }
    }

    private fun PassengerDashboardActivityRow.toActivityUiModel(): DashboardActivityUiModel {
        return DashboardActivityUiModel(
            id = "ticket_$id",
            title = "Billet $ticketNumber",
            subtitle = buildActivitySubtitle(
                primary = passengerName.ifBlank { "Passager" },
                routeLabel = routeLabel
            ),
            timestampLabel = createdAt.toDisplayTime(),
            amountLabel = formatCurrency(price, currency),
            kind = DashboardActivityKind.PassengerTicket,
            createdAt = createdAt
        )
    }

    private fun CargoDashboardActivityRow.toActivityUiModel(): DashboardActivityUiModel {
        return DashboardActivityUiModel(
            id = "cargo_$id",
            title = "Colis $ticketNumber",
            subtitle = buildActivitySubtitle(
                primary = receiverName.ifBlank { senderName.ifBlank { "Colis" } },
                routeLabel = routeLabel
            ),
            timestampLabel = createdAt.toDisplayTime(),
            amountLabel = formatCurrency(price, currency),
            kind = DashboardActivityKind.CargoTicket,
            createdAt = createdAt
        )
    }

    private fun ExpenseDashboardActivityRow.toActivityUiModel(): DashboardActivityUiModel {
        return DashboardActivityUiModel(
            id = "expense_$id",
            title = category.toExpenseTitle(),
            subtitle = buildActivitySubtitle(
                primary = description.ifBlank { "Depense du trajet" },
                routeLabel = routeLabel
            ),
            timestampLabel = createdAt.toDisplayTime(),
            amountLabel = formatCurrency(amount, currency),
            kind = DashboardActivityKind.Expense,
            createdAt = createdAt
        )
    }

    private fun String.toExpenseTitle(): String = when (this) {
        "fuel" -> "Carburant"
        "food" -> "Repas"
        "tolls" -> "Peage"
        "maintenance" -> "Maintenance"
        else -> "Autre depense"
    }
}
