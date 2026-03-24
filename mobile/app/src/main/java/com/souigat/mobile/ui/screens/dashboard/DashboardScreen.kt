package com.souigat.mobile.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.ui.components.StitchCard
import com.souigat.mobile.ui.components.StitchDivider
import com.souigat.mobile.ui.components.StitchIconDot
import com.souigat.mobile.ui.components.StitchMonoText
import com.souigat.mobile.ui.components.StitchPill
import com.souigat.mobile.ui.components.StitchPrimaryButton
import com.souigat.mobile.ui.components.StitchSectionLabel
import com.souigat.mobile.ui.components.StitchTileShape
import com.souigat.mobile.ui.theme.BrandBlueSoft
import com.souigat.mobile.ui.theme.BrandBlueStroke
import com.souigat.mobile.ui.theme.BrandMono
import com.souigat.mobile.ui.theme.ErrorRed
import com.souigat.mobile.ui.theme.Success
import com.souigat.mobile.ui.theme.SuccessSoft
import com.souigat.mobile.ui.theme.Warning
import com.souigat.mobile.ui.theme.WarningSoft

@Composable
fun DashboardScreen(
    onNavigateToTrips: () -> Unit = {},
    onNavigateToTripDetail: (Long) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val initials = remember(state.conductorFullName) {
        state.conductorFullName
            .split(" ")
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
            .ifBlank { "SC" }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Bonjour, ${state.conductorFirstName}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tableau de bord - ${state.lastSyncLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }

                val hasWarnings = state.pendingCount > 0 || state.quarantinedCount > 0
                val syncBg = if (hasWarnings) Warning else Success
                val syncFg = if (hasWarnings) WarningSoft else SuccessSoft
                val syncIcon = if (hasWarnings) Icons.Default.Sync else Icons.Default.CheckCircle
                val syncLabel = when {
                    state.quarantinedCount > 0 -> "Action requise"
                    state.pendingCount > 0 -> "Synchronisation en attente"
                    else -> "Synchronise"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(syncBg)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(syncIcon, contentDescription = null, tint = syncFg, modifier = Modifier.size(15.dp))
                    Text(
                        text = syncLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = syncFg
                    )
                }
            }
        },
        floatingActionButton = {
            StitchPrimaryButton(
                label = "MES TRAJETS",
                onClick = onNavigateToTrips,
                leadingIcon = Icons.Default.DirectionsBus,
                modifier = Modifier.height(54.dp)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.pendingCount > 0 || state.quarantinedCount > 0) {
                item("warning_banner") {
                    WarningBanner(
                        title = "Action requise",
                        message = when {
                            state.quarantinedCount > 0 ->
                                "${state.quarantinedCount} element(s) en quarantaine necessitent une verification."
                            else ->
                                "${state.pendingCount} element(s) attendent encore une synchronisation."
                        }
                    )
                }
            }

            item("hero_metric") {
                RevenueCard(metric = state.heroMetric)
            }

            item("route_card") {
                val route = state.route
                if (route == null) {
                    EmptyRouteCard(onNavigateToTrips = onNavigateToTrips)
                } else {
                    ActiveRouteCard(
                        route = route,
                        onClick = { route.tripId?.let(onNavigateToTripDetail) ?: onNavigateToTrips() }
                    )
                }
            }

            item("metrics_grid") {
                MetricsGrid(metrics = state.secondaryMetrics)
            }

            item("activity_title") {
                StitchSectionLabel("Activite recente", modifier = Modifier.padding(top = 4.dp))
            }

            item("activity_feed") {
                if (state.activity.isEmpty()) {
                    StitchCard {
                        Text(
                            text = "Aucune activite pour le moment.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    StitchCard(contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)) {
                        state.activity.forEachIndexed { index, item ->
                            DashboardActivityRow(item = item)
                            if (index < state.activity.lastIndex) {
                                StitchDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningBanner(title: String, message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(start = 0.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .heightIn(min = 92.dp)
                .background(Warning)
        )
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Warning, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Warning
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RevenueCard(metric: DashboardMetricUiModel) {
    val rawValue = metric.value.replace("DZD", "").trim()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BrandBlueSoft)
            .border(1.dp, BrandBlueStroke, RoundedCornerShape(24.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StitchSectionLabel("Revenus", color = MaterialTheme.colorScheme.primary)
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StitchMonoText(
                text = rawValue,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = BrandMono,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 38.sp
                ),
                color = MaterialTheme.colorScheme.primaryContainer
            )
            StitchMonoText(
                text = "DZD",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primaryContainer
            )
        }

        Text(
            text = metric.supporting ?: "Aucun trajet actif",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Success
        )
    }
}

@Composable
private fun ActiveRouteCard(route: DashboardRouteUiModel, onClick: () -> Unit) {
    StitchCard(
        modifier = Modifier.clickable(onClick = onClick),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .heightIn(min = 120.dp)
                    .background(Success)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    StitchPill(
                        text = route.statusLabel.uppercase(),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                    StitchPill(
                        text = route.priceLabel,
                        containerColor = SuccessSoft,
                        contentColor = Success
                    )
                }

                Text(
                    text = "${route.origin} -> ${route.destination}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StitchPill(
                        text = route.busPlate,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    StitchMonoText(
                        text = route.departureLabel.takeLast(5),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRouteCard(onNavigateToTrips: () -> Unit) {
    StitchCard {
        Text(
            text = "Aucun trajet actif",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Vos trajets assignes apparaitront ici des qu'ils seront disponibles.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StitchPrimaryButton(
            label = "Ouvrir les trajets",
            onClick = onNavigateToTrips,
            leadingIcon = Icons.Default.DirectionsBus,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MetricsGrid(metrics: List<DashboardMetricUiModel>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var index = 0
        while (index < metrics.size) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(metric = metrics[index], modifier = Modifier.weight(1f))
                if (index + 1 < metrics.size) {
                    MetricTile(metric = metrics[index + 1], modifier = Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            index += 2
        }
    }
}

@Composable
private fun MetricTile(metric: DashboardMetricUiModel, modifier: Modifier = Modifier) {
    val icon = when (metric.id) {
        "passengers" -> Icons.Default.Group
        "expenses" -> Icons.Default.Payments
        "pending" -> Icons.Default.HourglassEmpty
        "avg_ticket" -> Icons.Default.Assessment
        else -> Icons.Default.Assessment
    }

    StitchCard(
        modifier = modifier,
        shape = StitchTileShape,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StitchIconDot(icon = icon, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            StitchMonoText(
                text = metric.value.replace("DZD", "").trim(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DashboardActivityRow(item: DashboardActivityUiModel) {
    val icon = when (item.kind) {
        DashboardActivityKind.PassengerTicket -> Icons.Default.ConfirmationNumber
        DashboardActivityKind.CargoTicket -> Icons.Default.Inventory2
        DashboardActivityKind.Expense -> Icons.Default.Receipt
    }
    val tint = when (item.kind) {
        DashboardActivityKind.PassengerTicket -> Success
        DashboardActivityKind.CargoTicket -> MaterialTheme.colorScheme.primary
        DashboardActivityKind.Expense -> ErrorRed
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${item.timestampLabel} - ${item.subtitle}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        StitchMonoText(
            text = item.amountLabel,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (item.kind == DashboardActivityKind.Expense) ErrorRed else MaterialTheme.colorScheme.onSurface
        )
    }
}
