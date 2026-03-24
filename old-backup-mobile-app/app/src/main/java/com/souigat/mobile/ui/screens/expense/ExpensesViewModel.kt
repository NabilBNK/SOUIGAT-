package com.souigat.mobile.ui.screens.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.dao.ExpenseDao
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.entity.ExpenseEntity
import com.souigat.mobile.data.local.entity.TripEntity
import com.souigat.mobile.ui.model.TripFormHeaderUiModel
import com.souigat.mobile.util.formatCurrency
import com.souigat.mobile.util.toDisplayDateTime
import com.souigat.mobile.util.toRouteDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class ExpenseListItemUiModel(
    val id: Long,
    val categoryLabel: String,
    val description: String,
    val dateLabel: String,
    val amountLabel: String
)

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
                expenseDao.observeByTrip(trip.id),
                expenseDao.observeTotalAmount(trip.id)
            ) { expenses, total ->
                ExpensesUiState(
                    activeTripId = trip.id,
                    activeTripHeader = trip.toHeader(),
                    canCreateExpense = trip.status == "in_progress",
                    expenses = expenses.map { it.toUiModel() },
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

    private fun ExpenseEntity.toUiModel(): ExpenseListItemUiModel {
        return ExpenseListItemUiModel(
            id = id,
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
