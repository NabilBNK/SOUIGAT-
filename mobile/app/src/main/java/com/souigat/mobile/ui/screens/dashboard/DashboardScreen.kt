package com.souigat.mobile.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.ui.components.ActivityItem
import com.souigat.mobile.ui.components.ConductorPanelSurface
import com.souigat.mobile.ui.components.EmptyStatePanel
import com.souigat.mobile.ui.components.QuarantineWarningBanner
import com.souigat.mobile.ui.components.StatusPill
import com.souigat.mobile.ui.components.TripSummaryCard
import com.souigat.mobile.ui.theme.ErrorRed
import com.souigat.mobile.ui.theme.Success
import com.souigat.mobile.ui.theme.Warning

@Composable
fun DashboardScreen(
    onNavigateToTrips: () -> Unit = {},
    onNavigateToTripDetail: (Int) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToTrips,
                icon = { Icon(Icons.Default.DirectionsBus, contentDescription = null) },
                text = { Text(if (state.route != null) "Voir mes trajets" else "Mes trajets") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "hero", contentType = "hero") {
                DashboardHero(
                    firstName = state.conductorFirstName,
                    lastSyncLabel = state.lastSyncLabel,
                    pendingCount = state.pendingCount,
                    quarantinedCount = state.quarantinedCount
                )
            }

            if (state.quarantinedCount > 0) {
                item(key = "quarantine", contentType = "banner") {
                    QuarantineWarningBanner(quarantinedCount = state.quarantinedCount)
                }
            }

            item(key = "route", contentType = "route") {
                val route = state.route
                if (route != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.clickable {
                                route.tripId?.let(onNavigateToTripDetail) ?: onNavigateToTrips()
                            }
                        ) {
                            TripSummaryCard(
                                origin = route.origin,
                                destination = route.destination,
                                busPlate = route.busPlate,
                                departureLabel = route.departureLabel,
                                statusLabel = route.statusLabel,
                                supportingLabel = route.priceLabel
                            )
                        }
                        Text(
                            text = "Trajet actif",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                } else {
                    EmptyStatePanel(
                        icon = Icons.Default.DirectionsBus,
                        title = "Aucun trajet actif",
                        message = "Vos trajets assignes apparaitront ici des qu'ils sont disponibles.",
                        primaryActionLabel = "Ouvrir les trajets",
                        onPrimaryAction = onNavigateToTrips
                    )
                }
            }

            item(key = "hero_metric", contentType = "metric_highlight") {
                HeroMetricCard(metric = state.heroMetric)
            }

            item(key = "metrics", contentType = "metric_grid") {
                MetricsGrid(metrics = state.secondaryMetrics)
            }

            item(key = "activity_title", contentType = "section_header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Activite recente",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Billets, colis et depenses recents de vos trajets.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.activity.isEmpty()) {
                item(key = "activity_empty", contentType = "empty_state") {
                    EmptyStatePanel(
                        icon = Icons.Default.History,
                        title = "Aucune activite",
                        message = "Les prochaines creations hors ligne resteront visibles ici."
                    )
                }
            } else {
                items(
                    items = state.activity,
                    key = { it.id },
                    contentType = { "activity_item" }
                ) { item ->
                    ActivityItem(
                        icon = when (item.kind) {
                            DashboardActivityKind.PassengerTicket -> Icons.Default.ConfirmationNumber
                            DashboardActivityKind.CargoTicket -> Icons.Default.Inventory2
                            DashboardActivityKind.Expense -> Icons.Default.Receipt
                        },
                        iconTint = when (item.kind) {
                            DashboardActivityKind.PassengerTicket -> MaterialTheme.colorScheme.primary
                            DashboardActivityKind.CargoTicket -> Warning
                            DashboardActivityKind.Expense -> ErrorRed
                        },
                        title = item.title,
                        subtitle = item.subtitle,
                        timestamp = item.timestampLabel,
                        amount = item.amountLabel
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardHero(
    firstName: String,
    lastSyncLabel: String,
    pendingCount: Int,
    quarantinedCount: Int
) {
    val gradient = remember {
        Brush.linearGradient(
            colors = listOf(Color(0xFF0F172A), Color(0xFF1D4ED8))
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .background(gradient)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Bonjour, $firstName",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Tableau de bord terrain",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
                SyncPill(
                    pendingCount = pendingCount,
                    quarantinedCount = quarantinedCount
                )
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.84f),
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "Derniere synchronisation",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.76f)
                        )
                        Text(
                            text = lastSyncLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncPill(
    pendingCount: Int,
    quarantinedCount: Int
) {
    val (label, container, content) = when {
        quarantinedCount > 0 -> Triple("Attention", ErrorRed.copy(alpha = 0.24f), Color.White)
        pendingCount > 0 -> Triple("$pendingCount en attente", Warning.copy(alpha = 0.24f), Color.White)
        else -> Triple("Synchronise", Success.copy(alpha = 0.24f), Color.White)
    }

    StatusPill(
        text = label,
        containerColor = container,
        contentColor = content
    )
}

@Composable
private fun HeroMetricCard(metric: DashboardMetricUiModel) {
    val toneColor = metric.tone.toColor()

    ConductorPanelSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = metric.value,
                style = MaterialTheme.typography.displaySmall,
                color = toneColor,
                fontWeight = FontWeight.ExtraBold
            )
            if (!metric.supporting.isNullOrBlank()) {
                Text(
                    text = metric.supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetricsGrid(metrics: List<DashboardMetricUiModel>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { metric ->
                    DashboardMetricCard(
                        metric = metric,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DashboardMetricCard(
    metric: DashboardMetricUiModel,
    modifier: Modifier = Modifier
) {
    val toneColor = metric.tone.toColor()
    ConductorPanelSurface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = metric.value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = toneColor
            )
            if (!metric.supporting.isNullOrBlank()) {
                Text(
                    text = metric.supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DashboardMetricTone.toColor(): Color = when (this) {
    DashboardMetricTone.Primary -> Color(0xFF1D4ED8)
    DashboardMetricTone.Positive -> Success
    DashboardMetricTone.Negative -> ErrorRed
    DashboardMetricTone.Neutral -> MaterialTheme.colorScheme.onSurface
}
