package com.souigat.mobile.ui.screens.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.ui.components.ConductorPanelSurface
import com.souigat.mobile.ui.components.EmptyStatePanel
import com.souigat.mobile.ui.components.TripSummaryCard
import com.souigat.mobile.ui.theme.ErrorRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    onNavigateToCreate: (tripId: Long) -> Unit = {},
    viewModel: ExpensesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeTripId = state.activeTripId
    val activeTripHeader = state.activeTripHeader

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Depenses") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (state.canCreateExpense && activeTripId != null) {
                FloatingActionButton(onClick = { onNavigateToCreate(activeTripId) }) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter une depense")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (activeTripHeader != null) {
                item(key = "trip_header") {
                    TripSummaryCard(
                        origin = activeTripHeader.origin,
                        destination = activeTripHeader.destination,
                        busPlate = activeTripHeader.busPlate,
                        departureLabel = activeTripHeader.departureLabel,
                        statusLabel = activeTripHeader.statusLabel,
                        supportingLabel = state.totalLabel
                    )
                }
            }

            item(key = "summary") {
                ConductorPanelSurface(shape = MaterialTheme.shapes.extraLarge) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Total depenses",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (state.expenses.isEmpty()) {
                                    "Aucune depense"
                                } else {
                                    "${state.expenses.size} ligne(s)"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = state.totalLabel,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed
                        )
                    }
                }
            }

            if (state.expenses.isEmpty()) {
                item(key = "empty") {
                    EmptyStatePanel(
                        icon = Icons.Default.Receipt,
                        title = "Aucune depense enregistree",
                        message = if (state.activeTripHeader == null) {
                            "Demarrez un trajet pour enregistrer des depenses hors ligne."
                        } else {
                            "Les depenses du trajet en cours apparaitront ici."
                        },
                        primaryActionLabel = if (state.canCreateExpense) "Ajouter une depense" else null,
                        onPrimaryAction = if (state.canCreateExpense && activeTripId != null) {
                            { onNavigateToCreate(activeTripId) }
                        } else {
                            null
                        }
                    )
                }
            } else {
                items(
                    items = state.expenses,
                    key = { it.id },
                    contentType = { "expense_item" }
                ) { expense ->
                    ExpenseItemCard(expense = expense)
                }
            }
        }
    }
}

@Composable
private fun ExpenseItemCard(expense: ExpenseListItemUiModel) {
    ConductorPanelSurface(shape = MaterialTheme.shapes.extraLarge) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = expense.categoryLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = expense.dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = expense.amountLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ErrorRed
            )
        }
    }
}
