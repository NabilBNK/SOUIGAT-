package com.souigat.mobile.ui.screens.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.souigat.mobile.ui.components.EmptyStatePanel
import com.souigat.mobile.ui.theme.ErrorRed

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
                    Text(
                        text = "Mes trajets",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0D1117)
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { /* TODO Handle Menu Drawer */ }) {
                        Icon(Icons.Default.Menu, contentDescription = null, tint = Color(0xFF0D1117))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshTrips(force = true) },
                        enabled = !uiState.isRefreshing
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Rafraichir", tint = MaterialTheme.colorScheme.primary)
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item(key = "sync_status") {
                            // Sync Status Bar from Stitch
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CloudDone,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "ÉTAT DE SYNCHRONISATION",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = uiState.lastRefreshAt?.let { "Dernier sync" } ?: "En attente",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (uiState.isStale || uiState.errorMessage != null) ErrorRed else Color(0xFF34D399))
                                        )
                                    }
                                }
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
private fun TripListCard(
    trip: TripListItemUiModel,
    onClick: () -> Unit
) {
    val leftBorderColor = trip.statusTone.leftBorderColor()
    val pillBgColor = trip.statusTone.pillBackgroundColor()
    val pillTextColor = trip.statusTone.pillTextColor()
    val opacity = if (trip.statusTone == TripCardStatusTone.Completed) 0.8f else 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = opacity))
            .border(1.dp, Color(0xFFC3C5D7).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .shadow(1.dp, RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(leftBorderColor)
            )

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "${trip.origin} → ${trip.destination}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                        color = if (trip.statusTone == TripCardStatusTone.Cancelled) ErrorRed else MaterialTheme.colorScheme.onSurface 
                    )
                    Text(
                        text = trip.statusLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
                        color = pillTextColor,
                        modifier = Modifier
                            .background(pillBgColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = trip.departureLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (trip.statusTone == TripCardStatusTone.Completed) 0.6f else 1f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = trip.busPlate,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                    
                    val priceBgColor = if (trip.statusTone == TripCardStatusTone.Cancelled) MaterialTheme.colorScheme.surfaceContainerHighest else if (trip.statusTone == TripCardStatusTone.Completed) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer
                    val priceTextColor = if (trip.statusTone == TripCardStatusTone.Cancelled) ErrorRed.copy(alpha=0.5f) else if (trip.statusTone == TripCardStatusTone.Completed) MaterialTheme.colorScheme.onSurface else Color.White
                    
                    Text(
                        text = trip.priceLabel,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = priceTextColor,
                        modifier = Modifier
                            .background(priceBgColor, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TripCardStatusTone.leftBorderColor(): Color = when (this) {
    TripCardStatusTone.Scheduled -> Color(0xFF003FB1) // Primary
    TripCardStatusTone.InProgress -> Color(0xFF34D399) // Green
    TripCardStatusTone.Completed -> Color(0xFF555F6D) // Secondary
    TripCardStatusTone.Cancelled -> ErrorRed // Red
    TripCardStatusTone.Unknown -> Color(0xFFC3C5D7)
}

@Composable
private fun TripCardStatusTone.pillBackgroundColor(): Color = when (this) {
    TripCardStatusTone.Scheduled -> Color(0xFFDBE1FF)
    TripCardStatusTone.InProgress -> Color(0xFFDCFCE7)
    TripCardStatusTone.Completed -> Color(0xFFE0E3E6)
    TripCardStatusTone.Cancelled -> Color(0xFFFFDAD6)
    TripCardStatusTone.Unknown -> Color(0xFFF2F4F7)
}

@Composable
private fun TripCardStatusTone.pillTextColor(): Color = when (this) {
    TripCardStatusTone.Scheduled -> Color(0xFF003FB1)
    TripCardStatusTone.InProgress -> Color(0xFF166534)
    TripCardStatusTone.Completed -> Color(0xFF555F6D)
    TripCardStatusTone.Cancelled -> ErrorRed
    TripCardStatusTone.Unknown -> Color(0xFF596372)
}
