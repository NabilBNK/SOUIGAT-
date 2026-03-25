package com.souigat.mobile.ui.screens.expense

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.dao.ExpenseDao
import com.souigat.mobile.data.local.dao.ExpenseListRow
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.entity.TripEntity
import com.souigat.mobile.ui.model.TripFormHeaderUiModel
import com.souigat.mobile.util.formatCurrency
import com.souigat.mobile.util.toDisplayDate
import com.souigat.mobile.util.toDisplayDateTime
import com.souigat.mobile.util.toRouteDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@Immutable
data class ExpenseListItemUiModel(
    val id: Long,
    val sectionLabel: String,
    val categoryLabel: String,
    val description: String,
    val dateLabel: String,
    val amountLabel: String
)

@Stable
data class ExpensesUiState(
    val activeTripId: Long? = null,
    val activeTripHeader: TripFormHeaderUiModel? = null,
    val canCreateExpense: Boolean = false,
    val expenses: List<ExpenseListItemUiModel> = emptyList(),
    val totalLabel: String = formatCurrency(0L)
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpensesViewModel @Inject constructor(
    tripDao: TripDao,
    private val expenseDao: ExpenseDao
) : ViewModel() {

    val uiState = tripDao.observeActiveTrip().flatMapLatest { trip ->
        if (trip == null) {
            flowOf(ExpensesUiState())
        } else {
            combine(
                expenseDao.observeListByTrip(trip.id),
                expenseDao.observeTotalAmount(trip.id)
            ) { expenses, total ->
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                ExpensesUiState(
                    activeTripId = trip.id,
                    activeTripHeader = trip.toHeader(),
                    canCreateExpense = trip.status == "in_progress",
                    expenses = buildList(expenses.size) {
                        expenses.forEach { expense ->
                            add(expense.toUiModel(zone, today))
                        }
                    },
                    totalLabel = formatCurrency(total, trip.currency)
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExpensesUiState()
    )

    private fun TripEntity.toHeader(): TripFormHeaderUiModel {
        return TripFormHeaderUiModel(
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
    }

    private fun ExpenseListRow.toUiModel(
        zone: ZoneId,
        today: LocalDate
    ): ExpenseListItemUiModel {
        val expenseDate = Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate()
        return ExpenseListItemUiModel(
            id = id,
            sectionLabel = when (expenseDate) {
                today -> "Aujourd'hui"
                today.minusDays(1) -> "Hier"
                else -> createdAt.toDisplayDate()
            },
            categoryLabel = when (category) {
                "fuel" -> "Carburant"
                "food" -> "Repas"
                "maintenance" -> "Maintenance"
                "tolls" -> "Peage"
                else -> "Autre"
            },
            description = description.ifBlank { "Depense du trajet" },
            dateLabel = createdAt.toDisplayDateTime(),
            amountLabel = formatCurrency(amount, currency)
        )
    }
}
