package com.souigat.mobile.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.R
import com.souigat.mobile.ui.components.EmptyStatePanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateToDetail: (Long) -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("HISTORIQUE", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp), color = Color(0xFF1A56DB)) },
                navigationIcon = {
                    IconButton(onClick = { /* Menu */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color(0xFF1A56DB))
                    }
                },
                actions = {
                    Icon(
                        painter = painterResource(id = R.drawable.souigat_logo_no_background),
                        contentDescription = "Logo",
                        modifier = Modifier.height(32.dp).padding(end = 16.dp),
                        tint = Color.Unspecified
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF0F2F5)
                ),
                modifier = Modifier.border(1.dp, Color(0xFFE6E8EB))
            )
        },
        containerColor = Color(0xFFF0F2F5) // Background
    ) { paddingValues ->
        when (val current = state) {
            HistoryUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            HistoryUiState.Empty -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp), contentAlignment = Alignment.Center) {
                    EmptyStatePanel(
                        icon = Icons.Default.Schedule,
                        title = "Aucun trajet arché",
                        message = "Les trajets terminés ou annulés apparaitront ici."
                    )
                }
            }
            is HistoryUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    
                    // Note: Here we'd ideally group the items by Date (Month/Year). As an interim step, we'll just emulate throwing the header.
                    item(key = "header_mars") {
                        Text(
                            text = "HISTORIQUE COMPLET",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(
                        items = current.trips,
                        key = { it.id },
                        contentType = { "history_trip" }
                    ) { trip ->
                        HistoryTripCard(
                            trip = trip,
                            onClick = { onNavigateToDetail(trip.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTripCard(
    trip: HistoryTripUiModel,
    onClick: () -> Unit
) {
    val isCancelled = trip.isCancelled
    val indicatorColor = if (isCancelled) Color(0xFFDC2626) else Color(0xFF6B7280)
    val alpha = if (isCancelled) 0.8f else 1.0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha=alpha))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(indicatorColor))
            Column(modifier = Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Top
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${trip.origin} → ${trip.destination}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (isCancelled) TextDecoration.LineThrough else TextDecoration.None
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = if (isCancelled) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                            Text(
                                text = trip.dateLabel, // Time frame
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MaterialTheme.typography.bodySmall.fontFamily),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                textDecoration = if (isCancelled) TextDecoration.LineThrough else TextDecoration.None
                            )
                        }
                    }
                    Box(modifier = Modifier.background(if (isCancelled) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(
                            text = trip.statusLabel.uppercase().takeIf { isCancelled } ?: trip.fareLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                // Bottom
                Row(
                    modifier = Modifier.fillMaxWidth().border(0.dp, Color.Transparent).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            if (isCancelled) Icons.Default.Warning else Icons.Default.CheckCircle, 
                            contentDescription = null, 
                            tint = if (isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary, 
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isCancelled) "ANNULÉ/REMBOURSÉ" else "CONFIRMÉ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = if (isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        text = trip.busPlate, // Emulate ref
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
