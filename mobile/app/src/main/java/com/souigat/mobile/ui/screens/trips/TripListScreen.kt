package com.souigat.mobile.ui.screens.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.souigat.mobile.data.remote.dto.TripListDto
import com.souigat.mobile.domain.repository.TripException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    viewModel: TripListViewModel = hiltViewModel(),
    onNavigateToDetail: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Refresh trips every time screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.loadTrips()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes Trajets") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (val state = uiState) {
                is TripListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is TripListUiState.Error -> {
                    val msg = when (state.error) {
                        is TripException.NetworkUnavailable -> "Hors ligne. Vérifiez votre connexion."
                        else -> "Erreur lors du chargement des trajets."
                    }
                    Text(
                        text = msg,
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is TripListUiState.Success -> {
                    if (state.trips.isEmpty()) {
                        Text("Aucun trajet assigné.", modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.trips) { trip ->
                                TripItemCard(trip = trip, onClick = { onNavigateToDetail(trip.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripItemCard(trip: TripListDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(trip.origin, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Vers",
                        modifier = Modifier.padding(horizontal = 8.dp).size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(trip.destination, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                TripStatusBadge(status = trip.status)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.FRANCE)
            val parsedDate = try {
                OffsetDateTime.parse(trip.departureDatetime).format(formatter)
            } catch (e: Exception) {
                trip.departureDatetime
            }
            
            Text(
                text = "Départ : $parsedDate",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Bus : ${trip.plate}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TripStatusBadge(status: String) {
    val backgroundColor = when (status) {
        "scheduled" -> Color(0xFF2196F3)     // Blue
        "in_progress" -> Color(0xFFFFC107)   // Amber
        "completed" -> Color(0xFF4CAF50)     // Green
        "cancelled" -> Color(0xFF9E9E9E)     // Gray
        else -> Color.Gray
    }
    val text = when (status) {
        "scheduled" -> "Planifié"
        "in_progress" -> "En cours"
        "completed" -> "Terminé"
        "cancelled" -> "Annulé"
        else -> status
    }
    
    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
