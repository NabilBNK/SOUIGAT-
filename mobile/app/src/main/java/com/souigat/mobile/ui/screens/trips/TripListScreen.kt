package com.souigat.mobile.ui.screens.trips

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.R
import com.souigat.mobile.ui.components.StitchCard
import com.souigat.mobile.ui.components.StitchMonoText
import com.souigat.mobile.ui.components.StitchPill
import com.souigat.mobile.ui.components.StitchSectionLabel
import com.souigat.mobile.ui.theme.ErrorRed
import com.souigat.mobile.ui.theme.Success
import com.souigat.mobile.ui.theme.SuccessSoft

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    viewModel: TripListViewModel = hiltViewModel(),
    onNavigateToDetail: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onScreenVisible()
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
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.souigat_logo_no_background),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Mes trajets",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.refreshTrips(force = true) }) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Rafraichir",
                                tint = MaterialTheme.colorScheme.primaryContainer
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isInitialLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primaryContainer)
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item("sync_status") {
                            StitchCard(
                                shape = RoundedCornerShape(14.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                borderColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                shadowElevation = 0.dp,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CloudDone,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        StitchSectionLabel("Etat de synchronisation")
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (uiState.lastRefreshAt == null) "En attente" else "Dernier sync",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (uiState.isStale || uiState.errorMessage != null) ErrorRed else Success)
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.trips.isEmpty()) {
                            item("empty_state") {
                                StitchCard {
                                    Text(
                                        text = "Aucun trajet assigne",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = uiState.errorMessage
                                            ?: "Les trajets synchronises apparaitront ici apres le prochain rafraichissement.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
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
}

@Composable
private fun TripListCard(
    trip: TripListItemUiModel,
    onClick: () -> Unit
) {
    val borderColor = trip.statusTone.leftBorderColor()
    val statusContainer = trip.statusTone.pillBackgroundColor()
    val statusColor = trip.statusTone.pillTextColor()
    val amountContainer = when (trip.statusTone) {
        TripCardStatusTone.InProgress -> MaterialTheme.colorScheme.primaryContainer
        TripCardStatusTone.Scheduled -> MaterialTheme.colorScheme.primaryContainer
        TripCardStatusTone.Completed -> MaterialTheme.colorScheme.surfaceContainerHigh
        TripCardStatusTone.Cancelled -> MaterialTheme.colorScheme.errorContainer
        TripCardStatusTone.Unknown -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val amountColor = when (trip.statusTone) {
        TripCardStatusTone.InProgress,
        TripCardStatusTone.Scheduled -> MaterialTheme.colorScheme.onPrimaryContainer
        TripCardStatusTone.Cancelled -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    StitchCard(
        modifier = Modifier.clickable(onClick = onClick),
        shadowElevation = 0.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(110.dp)
                    .background(borderColor)
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
                    Text(
                        text = "${trip.origin} -> ${trip.destination}",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (trip.statusTone == TripCardStatusTone.Cancelled) ErrorRed else MaterialTheme.colorScheme.onSurface
                    )
                    StitchPill(
                        text = trip.statusLabel.uppercase(),
                        containerColor = statusContainer,
                        contentColor = statusColor
                    )
                }

                StitchMonoText(
                    text = trip.departureLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StitchPill(
                        text = trip.busPlate,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StitchPill(
                        text = trip.priceLabel,
                        containerColor = amountContainer,
                        contentColor = amountColor,
                        textStyle = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = com.souigat.mobile.ui.theme.BrandMono
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TripCardStatusTone.leftBorderColor(): Color = when (this) {
    TripCardStatusTone.Scheduled -> MaterialTheme.colorScheme.primary
    TripCardStatusTone.InProgress -> Success
    TripCardStatusTone.Completed -> MaterialTheme.colorScheme.onSurfaceVariant
    TripCardStatusTone.Cancelled -> ErrorRed
    TripCardStatusTone.Unknown -> MaterialTheme.colorScheme.outlineVariant
}

@Composable
private fun TripCardStatusTone.pillBackgroundColor(): Color = when (this) {
    TripCardStatusTone.Scheduled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    TripCardStatusTone.InProgress -> SuccessSoft
    TripCardStatusTone.Completed -> MaterialTheme.colorScheme.surfaceContainerHigh
    TripCardStatusTone.Cancelled -> MaterialTheme.colorScheme.errorContainer
    TripCardStatusTone.Unknown -> MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
private fun TripCardStatusTone.pillTextColor(): Color = when (this) {
    TripCardStatusTone.Scheduled -> MaterialTheme.colorScheme.primary
    TripCardStatusTone.InProgress -> Success
    TripCardStatusTone.Completed -> MaterialTheme.colorScheme.onSurfaceVariant
    TripCardStatusTone.Cancelled -> MaterialTheme.colorScheme.error
    TripCardStatusTone.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
}
