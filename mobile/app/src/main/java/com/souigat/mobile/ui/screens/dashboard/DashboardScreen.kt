package com.souigat.mobile.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.souigat.mobile.ui.components.*
import com.souigat.mobile.ui.theme.ErrorRed
import com.souigat.mobile.ui.theme.Success
import com.souigat.mobile.ui.theme.Warning
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatAmount(centimes: Long, currency: String): String {
    val units = centimes / 100.0
    return "${NumberFormat.getNumberInstance(Locale.FRANCE).format(units)} $currency"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToTrips: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Bonjour, ${state.conductorName.substringBefore(' ')} 👋",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tableau de bord",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    SyncStatusBadge(
                        pendingCount = state.pendingCount,
                        quarantinedCount = state.quarantinedCount,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (state.activeTrip != null) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToTrips,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Billet") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quarantine warning banner — non-dismissable
            if (state.quarantinedCount > 0) {
                item(key = "quarantine_banner") {
                    QuarantineWarningBanner(quarantinedCount = state.quarantinedCount)
                }
            }

            // Active trip / route card
            item(key = "route_card") {
                if (state.activeTrip != null) {
                    val trip = state.activeTrip!!
                    RouteInfoCard(
                        origin = trip.originOffice,
                        destination = trip.destinationOffice,
                        busPlate = trip.busPlate,
                        statusLabel = "En cours"
                    )
                } else {
                    NoActiveTripCard(onNavigateToTrips = onNavigateToTrips)
                }
            }

            // Stats grid
            item(key = "stats_grid") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val currency = state.currency
                    StatCard(
                        label = "Revenus",
                        value = formatAmount(state.totalRevenueCentimes, currency),
                        icon = Icons.Default.TrendingUp,
                        iconTint = Success,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Dépenses",
                        value = formatAmount(state.totalExpensesCentimes, currency),
                        icon = Icons.Default.TrendingDown,
                        iconTint = ErrorRed,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Passagers",
                        value = state.passengerCount.toString(),
                        icon = Icons.Default.People,
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "En attente sync",
                        value = state.pendingCount.toString(),
                        icon = Icons.Default.Sync,
                        iconTint = Warning,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recent activity section header
            if (state.recentTickets.isNotEmpty() || state.recentExpenses.isNotEmpty()) {
                item(key = "activity_header") {
                    Text(
                        text = "Activité récente",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Recent passenger tickets
                items(
                    items = state.recentTickets.take(5),
                    key = { "ticket_${it.id}" }
                ) { ticket ->
                    ActivityItem(
                        icon = Icons.Default.ConfirmationNumber,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Billet passager — ${ticket.ticketNumber}",
                        subtitle = "${ticket.passengerName} · ${formatAmount(ticket.price, ticket.currency)}",
                        timestamp = timeFormat.format(Date(ticket.createdAt))
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                // Recent expenses
                items(
                    items = state.recentExpenses.take(3),
                    key = { "expense_${it.id}" }
                ) { expense ->
                    val categoryLabel = when (expense.category) {
                        "fuel" -> "Carburant"
                        "food" -> "Repas"
                        "tolls" -> "Péage"
                        "maintenance" -> "Maintenance"
                        else -> "Autre"
                    }
                    ActivityItem(
                        icon = Icons.Default.Receipt,
                        iconTint = ErrorRed,
                        title = "Dépense — $categoryLabel",
                        subtitle = "${expense.description} · ${formatAmount(expense.amount, expense.currency)}",
                        timestamp = timeFormat.format(Date(expense.createdAt))
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Empty state when no active trip and no history
            if (state.activeTrip == null && state.recentTickets.isEmpty()) {
                item(key = "empty_state") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBus,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Aucun trajet actif",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Vos trajets assignés apparaîtront ici.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoActiveTripCard(onNavigateToTrips: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsBus,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aucun trajet en cours",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Sélectionnez un trajet pour commencer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onNavigateToTrips) {
                Text("Voir")
            }
        }
    }
}
