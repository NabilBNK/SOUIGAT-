package com.souigat.mobile.ui.screens.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.souigat.mobile.data.local.entity.ExpenseEntity
import com.souigat.mobile.ui.theme.ErrorRed
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val dateFormat = SimpleDateFormat("dd MMM · HH:mm", Locale.FRANCE)
private fun formatAmount(centimes: Long, currency: String): String {
    val units = centimes / 100.0
    return "${NumberFormat.getNumberInstance(Locale.FRANCE).format(units)} $currency"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    onNavigateToCreate: (tripId: Long, currency: String) -> Unit = { _, _ -> },
    viewModel: ExpensesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val trip = state.activeTrip

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dépenses") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (trip != null && trip.status == "in_progress") {
                FloatingActionButton(
                    onClick = { onNavigateToCreate(trip.id, trip.currency) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter dépense", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).fillMaxSize()
        ) {
            // Summary card
            if (state.expenses.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.08f)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total dépenses", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text(
                            text = formatAmount(state.totalCentimes, trip?.currency ?: "DZD"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed
                        )
                    }
                }
            }

            if (state.expenses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Receipt, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Text("Aucune dépense enregistrée", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (trip == null) {
                            Text("Démarrez un trajet pour ajouter des dépenses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.expenses, key = { it.id }) { expense ->
                        ExpenseItemCard(expense = expense, currency = trip?.currency ?: "DZD")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseItemCard(expense: ExpenseEntity, currency: String) {
    val categoryLabel = when (expense.category) {
        "fuel"        -> "⛽ Carburant"
        "food"        -> "🍽 Repas"
        "tolls"       -> "🛣 Péage"
        "maintenance" -> "🔧 Maintenance"
        else          -> "📦 Autre"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(categoryLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (expense.description.isNotBlank()) {
                    Text(expense.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(dateFormat.format(Date(expense.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = formatAmount(expense.amount, currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ErrorRed
            )
        }
    }
}
