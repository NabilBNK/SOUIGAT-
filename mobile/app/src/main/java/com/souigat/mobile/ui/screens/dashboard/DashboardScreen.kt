package com.souigat.mobile.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.ui.components.ActivityItem
import com.souigat.mobile.ui.components.EmptyStatePanel
import com.souigat.mobile.ui.theme.ErrorRed
import com.souigat.mobile.ui.theme.Success
import com.souigat.mobile.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToTrips: () -> Unit = {},
    onNavigateToTripDetail: (Int) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Bonjour, ${state.conductorFirstName}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0D1117)
                                )
                            )
                            Text(
                                text = "Tableau de bord · Dernier sync: ${state.lastSyncLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White
                    ),
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = Color(0xFFE2E5EA),
                        shape = RoundedCornerShape(0.dp)
                    )
                )
                // Sync Status Bar
                val syncBgColor = if (state.pendingCount > 0 || state.quarantinedCount > 0) Warning.copy(alpha=0.2f) else MaterialTheme.colorScheme.tertiaryContainer
                val syncTextColor = if (state.pendingCount > 0 || state.quarantinedCount > 0) Warning else MaterialTheme.colorScheme.onTertiaryContainer
                val syncIcon = if (state.pendingCount > 0 || state.quarantinedCount > 0) Icons.Default.Sync else Icons.Default.CheckCircle
                val syncLabel = if (state.quarantinedCount > 0) "Action requise" else if (state.pendingCount > 0) "Synchronisation en attente" else "✓ Synchronisé"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(syncBgColor)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(syncIcon, contentDescription = null, tint = syncTextColor, modifier = Modifier.size(16.dp))
                    Text(
                        text = syncLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
                        color = syncTextColor
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToTrips,
                icon = { Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = Color.White) },
                text = { Text(if (state.route != null) "Trajet actif" else "Mes trajets", color = Color.White, fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(16.dp))
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            if (state.quarantinedCount > 0) {
                item(key = "quarantine") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                            .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                            .background(Color.White)
                            .border(width = 1.dp, color = Color.White)
                            .padding(start = 4.dp), 
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(Color(0xFFB45309)))
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB45309))
                            Column {
                                Text("Action requise", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFFB45309)))
                                Text("${state.quarantinedCount} element(s) necessite attention.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item(key = "hero_metric") {
                HeroMetricCard(metric = state.heroMetric)
            }

            item(key = "route") {
                val route = state.route
                if (route != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable {
                                route.tripId?.let(onNavigateToTripDetail) ?: onNavigateToTrips()
                            }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Left border
                            Box(modifier = Modifier.width(4.dp).fillMaxHeight().align(Alignment.CenterVertically).background(MaterialTheme.colorScheme.tertiary))
                            
                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        text = route.statusLabel.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                                        color = Color.White,
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Text(
                                        text = route.priceLabel ?: "",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "${route.origin} → ${route.destination}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = route.busPlate,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(4.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = route.departureLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "metrics") {
                MetricsGrid(metrics = state.secondaryMetrics)
            }

            item(key = "activity_title") {
                Text(
                    text = "ACTIVITÉ RÉCENTE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (state.activity.isEmpty()) {
                item(key = "activity_empty") {
                    EmptyStatePanel(
                        icon = Icons.Default.History,
                        title = "Aucune activité",
                        message = "Les événements de vos trajets apparaitront ici."
                    )
                }
            } else {
                item(key = "activity_list") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    ) {
                        state.activity.forEachIndexed { index, item ->
                            val icon = when (item.kind) {
                                DashboardActivityKind.PassengerTicket -> Icons.Default.ConfirmationNumber
                                DashboardActivityKind.CargoTicket -> Icons.Default.Inventory2
                                DashboardActivityKind.Expense -> Icons.Default.Receipt
                            }
                            val iconTint = when (item.kind) {
                                DashboardActivityKind.PassengerTicket -> MaterialTheme.colorScheme.tertiary
                                DashboardActivityKind.CargoTicket -> Warning
                                DashboardActivityKind.Expense -> ErrorRed
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text(text = item.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                        Text(text = "${item.timestampLabel} · ${item.subtitle}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(
                                    text = item.amountLabel,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (item.kind == DashboardActivityKind.Expense) ErrorRed else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (index < state.activity.size - 1) {
                                Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetricCard(metric: DashboardMetricUiModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFEBF0FF))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Column {
            Text(
                text = "REVENUS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.Baseline,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = metric.value.replace(Regex("[A-Za-z]"), "").trim(),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 38.sp),
                    color = Color(0xFF1A56DB)
                )
                Text(
                    text = "DZD",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF1A56DB)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (!metric.supporting.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = metric.supporting,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
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
    val icon = when(metric.label.lowercase()) {
        "passagers" -> Icons.Default.Group
        "dépenses", "depenses" -> Icons.Default.Payments
        "en attente" -> Icons.Default.HourglassEmpty
        "ticket moy." -> Icons.Default.ReceiptLong
        else -> Icons.Default.Assessment
    }

    Box(
        modifier = modifier
            .height(72.dp)
            .shadow(2.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${metric.value.replace(Regex("[A-Za-z]"), "").trim()}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
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
