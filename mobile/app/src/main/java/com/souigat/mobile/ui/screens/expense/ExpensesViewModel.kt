package com.souigat.mobile.ui.screens.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.dao.ExpenseDao
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.entity.ExpenseEntity
import com.souigat.mobile.data.local.entity.TripEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ExpensesUiState(
    val activeTrip: TripEntity? = null,
    val expenses: List<ExpenseEntity> = emptyList(),
    val totalCentimes: Long = 0L
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val tripDao: TripDao,
    private val expenseDao: ExpenseDao
) : ViewModel() {

    private val activeTrip: Flow<TripEntity?> = tripDao.observeActiveTrip()

    val uiState: StateFlow<ExpensesUiState> = activeTrip.flatMapLatest { trip ->
        if (trip == null) {
            flowOf(ExpensesUiState())
        } else {
            combine(
                expenseDao.observeByTrip(trip.id),
                expenseDao.observeTotalAmount(trip.id)
            ) { expenses, total ->
                ExpensesUiState(
                    activeTrip = trip,
                    expenses = expenses,
                    totalCentimes = total
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExpensesUiState()
    )
}
