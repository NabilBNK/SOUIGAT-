package com.souigat.mobile.ui.screens.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.ui.components.StitchCard
import com.souigat.mobile.ui.components.StitchMonoText
import com.souigat.mobile.ui.components.StitchPrimaryButton
import com.souigat.mobile.ui.components.StitchSectionLabel
import com.souigat.mobile.ui.theme.ErrorRed

@Composable
fun ExpensesScreen(
    onNavigateToCreate: (tripId: Long) -> Unit = {},
    viewModel: ExpensesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val groupedExpenses = remember(state.expenses) {
        state.expenses.groupBy { it.sectionLabel }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                state.activeTripHeader?.let { header ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${header.origin.uppercase()}  ->  ${header.destination.uppercase()}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Depenses",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Total: ${state.totalLabel}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = ErrorRed
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    ExpenseSummaryCell(
                        text = state.totalLabel,
                        modifier = Modifier.weight(1f)
                    )
                    ExpenseSummaryCell(
                        text = "${state.expenses.size} lignes",
                        modifier = Modifier.weight(1f)
                    )
                    ExpenseSummaryCell(
                        text = state.activeTripHeader?.currency ?: "DZD",
                        modifier = Modifier.weight(1f),
                        showDivider = false
                    )
                }
            }
        },
        floatingActionButton = {
            val activeTripId = state.activeTripId
            if (state.canCreateExpense && activeTripId != null) {
                FloatingActionButton(
                    onClick = { onNavigateToCreate(activeTripId) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter une depense")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (state.expenses.isEmpty()) {
                item("empty_state") {
                    EmptyExpensesState(
                        canCreateExpense = state.canCreateExpense,
                        onCreateExpense = {
                            state.activeTripId?.let(onNavigateToCreate)
                        }
                    )
                }
            } else {
                groupedExpenses.forEach { (sectionLabel, expenses) ->
                    item("section_$sectionLabel") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StitchSectionLabel(sectionLabel)
                            Spacer(modifier = Modifier.width(12.dp))
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        }
                    }

                    items(
                        items = expenses,
                        key = { it.id }
                    ) { expense ->
                        ExpenseRow(expense = expense)
                    }
                }

                item("receipt_card") {
                    StitchCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "NOTE DE FRAIS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.primaryContainer
                        )
                        Text(
                            text = "Besoin d'ajouter un justificatif ? Prenez une photo directement depuis l'application.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StitchPrimaryButton(
                            label = "AJOUTER RECU",
                            onClick = { state.activeTripId?.let(onNavigateToCreate) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseSummaryCell(
    text: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    Row(
        modifier = modifier
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            StitchMonoText(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
private fun EmptyExpensesState(
    canCreateExpense: Boolean,
    onCreateExpense: () -> Unit
) {
    StitchCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Aucune depense enregistree",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (canCreateExpense) {
                "Ajoutez votre premiere depense pour alimenter le rapport hors ligne."
            } else {
                "Les depenses apparaitront ici des qu'un trajet en cours sera disponible."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (canCreateExpense) {
            StitchPrimaryButton(
                label = "AJOUTER UNE DEPENSE",
                onClick = onCreateExpense,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseListItemUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = expenseCategoryIcon(expense.categoryLabel),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primaryContainer
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = expense.categoryLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                StitchMonoText(
                    text = expense.amountLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ErrorRed
                )
            }
            Text(
                text = expense.description.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun expenseCategoryIcon(categoryLabel: String): ImageVector {
    return when {
        categoryLabel.contains("Carburant", ignoreCase = true) -> Icons.Default.LocalGasStation
        categoryLabel.contains("Repas", ignoreCase = true) -> Icons.Default.Restaurant
        categoryLabel.contains("Maintenance", ignoreCase = true) -> Icons.Default.Build
        categoryLabel.contains("Peage", ignoreCase = true) -> Icons.Default.Toll
        else -> Icons.Default.MoreHoriz
    }
}
