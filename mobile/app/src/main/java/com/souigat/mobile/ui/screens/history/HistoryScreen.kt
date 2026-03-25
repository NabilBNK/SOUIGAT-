package com.souigat.mobile.ui.screens.history

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.R
import com.souigat.mobile.ui.components.StitchCard
import com.souigat.mobile.ui.components.StitchDivider
import com.souigat.mobile.ui.components.StitchMonoText
import com.souigat.mobile.ui.components.StitchPill
import com.souigat.mobile.ui.components.StitchSectionLabel
import com.souigat.mobile.ui.theme.ErrorRed
import com.souigat.mobile.ui.theme.Success

@Composable
fun HistoryScreen(
    onNavigateToDetail: (Long) -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
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
                    Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.primaryContainer)
                }
                Icon(
                    painter = painterResource(id = R.drawable.souigat_logo_no_background),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "HISTORIQUE",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.primaryContainer
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    ) { paddingValues ->
        when (val current = state) {
            HistoryUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primaryContainer)
                }
            }

            HistoryUiState.Empty -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    StitchCard(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Aucun trajet archive",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Les trajets termines ou annules apparaitront ici.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is HistoryUiState.Success -> {
                val groupedTrips = remember(current.trips) { current.trips.groupBy { it.monthLabel } }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    groupedTrips.forEach { (month, trips) ->
                        item("header_$month") {
                            StitchSectionLabel(month)
                        }

                        items(
                            items = trips,
                            key = { it.id },
                            contentType = { if (it.isCancelled) "history_cancelled" else "history_completed" }
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
}

@Composable
private fun HistoryTripCard(trip: HistoryTripUiModel, onClick: () -> Unit) {
    val stripeColor = if (trip.isCancelled) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
    val tone = if (trip.isCancelled) ErrorRed else Success

    StitchCard(
        modifier = Modifier.clickable(onClick = onClick),
        shadowElevation = 0.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(132.dp)
                    .background(stripeColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${trip.origin} -> ${trip.destination}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (trip.isCancelled) TextDecoration.LineThrough else TextDecoration.None
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                            StitchMonoText(
                                text = trip.dateLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    StitchPill(
                        text = if (trip.isCancelled) "Annule" else trip.fareLabel,
                        containerColor = if (trip.isCancelled) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (trip.isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }

                StitchDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (trip.isCancelled) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = tone,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (trip.isCancelled) "REMBOURSE" else "CONFIRME",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = tone
                        )
                    }
                    StitchMonoText(
                        text = "REF: ${trip.busPlate}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
