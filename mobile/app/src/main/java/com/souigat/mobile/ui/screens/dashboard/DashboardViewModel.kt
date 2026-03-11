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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class DashboardUiState(
    val conductorName: String = "",
    val activeTrip: TripEntity? = null,
    val passengerCount: Int = 0,
    val totalRevenueCentimes: Long = 0L,
    val totalExpensesCentimes: Long = 0L,
    val currency: String = "DZD",
    val recentTickets: List<PassengerTicketEntity> = emptyList(),
    val recentExpenses: List<ExpenseEntity> = emptyList(),
    val pendingCount: Int = 0,
    val quarantinedCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val tripDao: TripDao,
    private val ticketDao: PassengerTicketDao,
    private val expenseDao: ExpenseDao,
    private val syncQueueDao: SyncQueueDao,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val activeTrip: Flow<TripEntity?> = tripDao.observeActiveTrip()

    // Derive per-trip stats from the active trip id, flat-mapping each time it changes.
    private val revenueFlow: Flow<Long> = activeTrip.flatMapLatest { trip ->
        if (trip != null) ticketDao.observeTotalRevenue(trip.id)
        else flowOf(0L)
    }

    private val expensesFlow: Flow<Long> = activeTrip.flatMapLatest { trip ->
        if (trip != null) expenseDao.observeTotalAmount(trip.id)
        else flowOf(0L)
    }

    private val recentTicketsFlow: Flow<List<PassengerTicketEntity>> = activeTrip.flatMapLatest { trip ->
        if (trip != null) ticketDao.observeRecentByTrip(trip.id)
        else flowOf(emptyList())
    }

    private val recentExpensesFlow: Flow<List<ExpenseEntity>> = activeTrip.flatMapLatest { trip ->
        if (trip != null) expenseDao.observeRecentByTrip(trip.id)
        else flowOf(emptyList())
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        activeTrip,
        revenueFlow,
        expensesFlow,
        recentTicketsFlow,
        recentExpensesFlow,
        syncQueueDao.observePendingCount(),
        syncQueueDao.observeQuarantinedCount()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val trip              = values[0] as TripEntity?
        val revenue           = values[1] as Long
        val expenses          = values[2] as Long
        val tickets           = values[3] as List<PassengerTicketEntity>
        val expenseItems      = values[4] as List<ExpenseEntity>
        val pendingCount      = values[5] as Int
        val quarantinedCount  = values[6] as Int

        DashboardUiState(
            conductorName       = tokenManager.getFullName() ?: "Conducteur",
            activeTrip          = trip,
            passengerCount      = tickets.count { it.status == "active" },
            totalRevenueCentimes = revenue,
            totalExpensesCentimes = expenses,
            currency            = trip?.currency ?: "DZD",
            recentTickets       = tickets,
            recentExpenses      = expenseItems,
            pendingCount        = pendingCount,
            quarantinedCount    = quarantinedCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(conductorName = tokenManager.getFullName() ?: "Conducteur")
    )
}
