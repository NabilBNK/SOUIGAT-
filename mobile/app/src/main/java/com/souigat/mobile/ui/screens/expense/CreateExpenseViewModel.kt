package com.souigat.mobile.ui.screens.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CreateExpenseUiState {
    object Idle : CreateExpenseUiState()
    object Loading : CreateExpenseUiState()
    data class Success(val message: String) : CreateExpenseUiState()
    data class Error(val message: String) : CreateExpenseUiState()
}

@HiltViewModel
class CreateExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Safely extract tripId mirroring Ticket pattern to prevent ClassCastException
    val tripId: Long = savedStateHandle.get<Any>("tripId")?.toString()?.toLong() ?: error("tripId required")
    val currency: String = savedStateHandle.get<String>("currency") ?: "DZD"

    private val _uiState = MutableStateFlow<CreateExpenseUiState>(CreateExpenseUiState.Idle)
    val uiState: StateFlow<CreateExpenseUiState> = _uiState.asStateFlow()

    fun createExpense(
        amountStr: String,
        category: String,
        description: String
    ) {
        val amount = amountStr.toLongOrNull()
        
        if (amount == null || amount <= 0) {
            _uiState.value = CreateExpenseUiState.Error("Veuillez entrer un montant valide.")
            return
        }
        
        if (category.isBlank()) {
            _uiState.value = CreateExpenseUiState.Error("Veuillez sélectionner une catégorie.")
            return
        }

        viewModelScope.launch {
            _uiState.value = CreateExpenseUiState.Loading
            
            expenseRepository.createExpense(
                tripId = tripId,
                amount = amount,
                currency = currency,
                category = category,
                description = description
            ).onSuccess { expense ->
                _uiState.value = CreateExpenseUiState.Success("Dépense enregistrée hors ligne avec succès.")
            }.onFailure {
                _uiState.value = CreateExpenseUiState.Error("Erreur lors de l'enregistrement de la dépense.")
            }
        }
    }

    fun resetState() {
        _uiState.value = CreateExpenseUiState.Idle
    }
}
