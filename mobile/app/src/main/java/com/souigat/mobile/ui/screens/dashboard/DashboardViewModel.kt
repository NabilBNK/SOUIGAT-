package com.souigat.mobile.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.local.dao.ExpenseDao
import com.souigat.mobile.data.local.dao.PassengerTicketDao
import com.souigat.mobile.data.local.dao.SyncQueueDao
import com.souigat.mobile.data.local.dao.TripDao
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

enum class DashboardMetricTone {
    Primary,
    Positive,
    Negative,
    Neutral
}

enum class DashboardActivityKind {
    PassengerTicket,
    Expense
}

data class DashboardRouteUiModel(
    val tripId: Int?,
    val origin: String,
    val destination: String,
    val busPlate: String,
    val departureLabel: String,
    val priceLabel: String,
    val statusLabel: String
)

data class DashboardMetricUiModel(
    val id: String,
    val label: String,
    val value: String,
    val supporting: String? = null,
    val tone: DashboardMetricTone = DashboardMetricTone.Neutral
)

data class DashboardActivityUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val timestampLabel: String,
    val amountLabel: String,
    val kind: DashboardActivityKind,
    val createdAt: Long
)

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
    val totalExpensesCentimes: Long = 0L,
    val recentTickets: List<PassengerTicketEntity> = emptyList(),
    val recentExpenses: List<ExpenseEntity> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    tripDao: TripDao,
    private val ticketDao: PassengerTicketDao,
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
                expenseDao.observeTotalAmount(trip.id),
                ticketDao.observeRecentByTrip(trip.id),
                expenseDao.observeRecentByTrip(trip.id)
            ) { passengerCount, revenue, expenses, tickets, expenseItems ->
                DashboardTripStats(
                    activeTrip = trip,
                    passengerCount = passengerCount,
                    totalRevenueCentimes = revenue,
                    totalExpensesCentimes = expenses,
                    recentTickets = tickets,
                    recentExpenses = expenseItems
                )
            }
        }
    }

    val uiState = combine(
        tripStatsFlow,
        syncQueueDao.observePendingCount(),
        syncQueueDao.observeQuarantinedCount(),
        syncQueueDao.observeLastSyncedAt()
    ) { tripStats, pendingCount, quarantinedCount, lastSyncedAt ->
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
            activity = buildActivityFeed(
                tickets = tripStats.recentTickets,
                expenses = tripStats.recentExpenses
            ),
            pendingCount = pendingCount,
            quarantinedCount = quarantinedCount,
            lastSyncLabel = lastSyncedAt?.toDisplayDateTime() ?: "Pas encore synchronise"
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(
            conductorFirstName = tokenManager.getFirstName() ?: "Conducteur",
            conductorFullName = tokenManager.getFullName() ?: "Conducteur"
        )
    )

    private fun TripEntity.toDashboardRouteUiModel(): DashboardRouteUiModel {
        return DashboardRouteUiModel(
            tripId = serverId?.toInt(),
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
        expenses: List<ExpenseEntity>
    ): List<DashboardActivityUiModel> {
        val ticketItems = tickets.take(4).map { ticket ->
            DashboardActivityUiModel(
                id = "ticket_${ticket.id}",
                title = "Billet ${ticket.ticketNumber}",
                subtitle = ticket.passengerName.ifBlank { "Passager" },
                timestampLabel = ticket.createdAt.toDisplayTime(),
                amountLabel = formatCurrency(ticket.price, ticket.currency),
                kind = DashboardActivityKind.PassengerTicket,
                createdAt = ticket.createdAt
            )
        }

        val expenseItems = expenses.take(4).map { expense ->
            DashboardActivityUiModel(
                id = "expense_${expense.id}",
                title = expense.category.toExpenseTitle(),
                subtitle = expense.description.ifBlank { "Depense du trajet" },
                timestampLabel = expense.createdAt.toDisplayTime(),
                amountLabel = formatCurrency(expense.amount, expense.currency),
                kind = DashboardActivityKind.Expense,
                createdAt = expense.createdAt
            )
        }

        return (ticketItems + expenseItems)
            .sortedByDescending { it.createdAt }
            .take(6)
    }

    private fun String.toExpenseTitle(): String = when (this) {
        "fuel" -> "Carburant"
        "food" -> "Repas"
        "tolls" -> "Peage"
        "maintenance" -> "Maintenance"
        else -> "Autre depense"
    }
}
