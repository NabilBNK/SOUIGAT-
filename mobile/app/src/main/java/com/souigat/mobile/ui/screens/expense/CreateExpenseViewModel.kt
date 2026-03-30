package com.souigat.mobile.ui.screens.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.local.ExpenseFormDraft
import com.souigat.mobile.data.local.FormDraftStore
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.entity.TripEntity
import com.souigat.mobile.domain.repository.ExpenseRepository
import com.souigat.mobile.ui.model.TripFormHeaderUiModel
import com.souigat.mobile.util.parseCurrencyInput
import com.souigat.mobile.util.toRouteDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
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

sealed class CreateExpenseUiState {
    object Idle : CreateExpenseUiState()
    object Loading : CreateExpenseUiState()
    data class Success(val message: String) : CreateExpenseUiState()
    data class Error(val message: String) : CreateExpenseUiState()
}

sealed class ExpenseFormHeaderState {
    object Loading : ExpenseFormHeaderState()
    data class Ready(val header: TripFormHeaderUiModel) : ExpenseFormHeaderState()
    data class Error(val message: String) : ExpenseFormHeaderState()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CreateExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
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
                ExpenseFormHeaderState.Error(
                    "Trajet introuvable localement. Retournez a la liste et rafraichissez."
                )
            } else {
                ExpenseFormHeaderState.Ready(trip.toFormHeader())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExpenseFormHeaderState.Loading
        )

    private val _uiState = MutableStateFlow<CreateExpenseUiState>(CreateExpenseUiState.Idle)
    val uiState: StateFlow<CreateExpenseUiState> = _uiState.asStateFlow()

    private val _draftState = MutableStateFlow(formDraftStore.getExpenseDraft(tripId))
    val draftState: StateFlow<ExpenseFormDraft> = _draftState.asStateFlow()

    fun createExpense(
        amountInput: String,
        category: String,
        description: String
    ) {
        val header = (formState.value as? ExpenseFormHeaderState.Ready)?.header
            ?: run {
                _uiState.value = CreateExpenseUiState.Error(
                    "Le trajet n'est pas disponible localement. Reessayez apres rafraichissement."
                )
                return
            }

        val amount = parseCurrencyInput(amountInput)
        val categoryClean = category.trim()
        val descriptionClean = description.trim()

        if (amount == null || amount <= 0) {
            _uiState.value = CreateExpenseUiState.Error("Veuillez entrer un montant valide.")
            return
        }
        if (categoryClean !in VALID_CATEGORIES) {
            _uiState.value = CreateExpenseUiState.Error("Veuillez selectionner une categorie valide.")
            return
        }
        if (descriptionClean.length > 200) {
            _uiState.value = CreateExpenseUiState.Error("La description ne doit pas depasser 200 caracteres.")
            return
        }

        viewModelScope.launch {
            _uiState.value = CreateExpenseUiState.Loading

            expenseRepository.createExpense(
                tripId = tripId,
                amount = amount,
                currency = header.currency,
                category = categoryClean,
                description = descriptionClean
            ).onSuccess {
                clearDraft()
                _uiState.value = CreateExpenseUiState.Success("Depense enregistree hors ligne avec succes.")
            }.onFailure { error ->
                _uiState.value = CreateExpenseUiState.Error(
                    error.message?.takeIf { it.isNotBlank() } ?: "Erreur lors de l'enregistrement de la depense."
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = CreateExpenseUiState.Idle
    }

    fun retryLookup() {
        lookupRequests.value += 1
    }

    fun persistDraft(draft: ExpenseFormDraft) {
        _draftState.value = draft
        formDraftStore.saveExpenseDraft(tripId, draft)
    }

    private fun clearDraft() {
        val cleared = ExpenseFormDraft()
        _draftState.value = cleared
        formDraftStore.clearExpenseDraft(tripId)
    }

    private fun TripEntity.toFormHeader(): TripFormHeaderUiModel {
        return TripFormHeaderUiModel(
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
    }

    companion object {
        private val VALID_CATEGORIES = setOf("fuel", "food", "maintenance", "tolls", "other")
    }
}
