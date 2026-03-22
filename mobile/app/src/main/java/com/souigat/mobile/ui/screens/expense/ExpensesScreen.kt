package com.souigat.mobile.ui.screens.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.ui.components.EmptyStatePanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    onNavigateToCreate: (tripId: Long) -> Unit = {},
    viewModel: ExpensesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeTripId = state.activeTripId

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC)).border(1.dp, Color(0xFFE2E5EA))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { /* Handle back if needed */ }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Color(0xFF1D4ED8)) // text-blue-700
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Dépenses", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), color = Color(0xFF0F172A))
                            Text("Total: ${state.totalLabel}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFDC2626)) // text-red-600
                        }
                    }
                    IconButton(onClick = { /* Menu Options */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color(0xFF1D4ED8))
                    }
                }
                
                // Summary KPI Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f))
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f)), contentAlignment = Alignment.Center) {
                        Text(state.totalLabel, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f)), contentAlignment = Alignment.Center) {
                        Text("${state.expenses.size} LIGNES", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant) // Uppercase
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text("DZD", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        floatingActionButton = {
            if (state.canCreateExpense && activeTripId != null) {
                FloatingActionButton(
                    onClick = { onNavigateToCreate(activeTripId) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
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
            contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
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
                item(key = "today_header") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Text(
                            text = "DÉPENSES DU TRAJET",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Box(modifier = Modifier.weight(1f).padding(start = 16.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f)))
                    }
                }
                
                items(
                    items = state.expenses,
                    key = { it.id },
                    contentType = { "expense_item" }
                ) { expense ->
                    ExpenseItemCard(expense = expense)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f), modifier = Modifier.padding(vertical = 4.dp))
                }
                
                item(key = "bento_card") {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f), RoundedCornerShape(12.dp))
                                .padding(20.dp)
                        ) {
                            Column {
                                Text(
                                    text = "NOTE DE FRAIS",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = "Besoin d'ajouter un justificatif ? Cette fonctionnalité sera bientôt disponible.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseItemCard(expense: ExpenseListItemUiModel) {
    val icon = when {
        expense.categoryLabel.contains("Carburant", ignoreCase = true) -> Icons.Default.LocalGasStation
        expense.categoryLabel.contains("Repas", ignoreCase = true) -> Icons.Default.Restaurant
        expense.categoryLabel.contains("Entretien", ignoreCase = true) -> Icons.Default.Build
        else -> Icons.Default.Receipt
    }
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(24.dp))
        }
        
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(
                    text = expense.categoryLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = expense.amountLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFDC2626) // text-red-600
                )
            }
            Text(
                text = expense.description.ifBlank { "Aucune description" },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
