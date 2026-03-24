package com.souigat.mobile.ui.screens.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.souigat.mobile.ui.components.ConductorPanelSurface
import com.souigat.mobile.ui.components.EmptyStatePanel
import com.souigat.mobile.ui.components.StatusPill
import com.souigat.mobile.ui.theme.ErrorRed
import com.souigat.mobile.ui.theme.Success
import com.souigat.mobile.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    viewModel: TripListViewModel = hiltViewModel(),
    onNavigateToDetail: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onScreenVisible()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mes trajets")
                        Text(
                            text = if (uiState.lastRefreshAt == null) {
                                "Chargement local prioritaire"
                            } else if (uiState.isStale) {
                                "Donnees locales en attente de rafraichissement"
                            } else {
                                "Trajets recents disponibles hors ligne"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshTrips(force = true) },
                        enabled = !uiState.isRefreshing
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rafraichir")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshTrips(force = true) },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isInitialLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.trips.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStatePanel(
                            icon = Icons.Default.CloudOff,
                            title = "Aucun trajet assigne",
                            message = uiState.errorMessage
                                ?: "Les trajets synchronises apparaitront ici. Tirez vers le bas ou utilisez le bouton de rafraichissement.",
                            primaryActionLabel = "Rafraichir",
                            onPrimaryAction = { viewModel.refreshTrips(force = true) }
                        )
                    }
                }

                else -> {
                    val errorMessage = uiState.errorMessage
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (errorMessage != null) {
                            item(key = "error_banner", contentType = "banner") {
                                BannerCard(
                                    title = "Rafraichissement incomplet",
                                    message = errorMessage,
                                    tone = ErrorRed,
                                    actionLabel = "Reessayer",
                                    onAction = { viewModel.refreshTrips(force = true) },
                                    secondaryLabel = "Ignorer",
                                    onSecondaryAction = viewModel::clearError
                                )
                            }
                        } else if (uiState.isStale) {
                            item(key = "stale_banner", contentType = "banner") {
                                BannerCard(
                                    title = "Donnees locales affichees",
                                    message = "La liste reste disponible hors ligne. Tirez pour verifier les nouvelles assignations.",
                                    tone = Warning,
                                    actionLabel = "Actualiser",
                                    onAction = { viewModel.refreshTrips(force = true) }
                                )
                            }
                        }

                        items(
                            items = uiState.trips,
                            key = { it.id },
                            contentType = { "trip_card" }
                        ) { trip ->
                            TripListCard(
                                trip = trip,
                                onClick = { onNavigateToDetail(trip.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerCard(
    title: String,
    message: String,
    tone: Color,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    ConductorPanelSurface(
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = tone.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tone
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
                if (secondaryLabel != null && onSecondaryAction != null) {
                    OutlinedButton(onClick = onSecondaryAction) {
                        Text(secondaryLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun TripListCard(
    trip: TripListItemUiModel,
    onClick: () -> Unit
) {
    ConductorPanelSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = trip.statusLabel,
                    containerColor = trip.statusTone.containerColor(),
                    contentColor = trip.statusTone.contentColor()
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${trip.origin} -> ${trip.destination}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = trip.departureLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = trip.busPlate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = trip.priceLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TripCardStatusTone.containerColor(): Color = when (this) {
    TripCardStatusTone.Scheduled -> Warning.copy(alpha = 0.18f)
    TripCardStatusTone.InProgress -> Success.copy(alpha = 0.18f)
    TripCardStatusTone.Completed -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
    TripCardStatusTone.Cancelled -> ErrorRed.copy(alpha = 0.16f)
    TripCardStatusTone.Unknown -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun TripCardStatusTone.contentColor(): Color = when (this) {
    TripCardStatusTone.Scheduled -> Color(0xFF8A5A00)
    TripCardStatusTone.InProgress -> Success
    TripCardStatusTone.Completed -> MaterialTheme.colorScheme.primary
    TripCardStatusTone.Cancelled -> ErrorRed
    TripCardStatusTone.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
}
