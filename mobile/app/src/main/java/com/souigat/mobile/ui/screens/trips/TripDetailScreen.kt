package com.souigat.mobile.ui.screens.trips

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.souigat.mobile.data.remote.dto.TripDetailDto
import com.souigat.mobile.domain.repository.TripException
import com.souigat.mobile.data.local.entity.CargoTicketEntity
import com.souigat.mobile.data.local.entity.PassengerTicketEntity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    viewModel: TripDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToCreateTicket: (Int, String, String, String, String, String) -> Unit = { _, _, _, _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val passengerTickets by viewModel.passengerTickets.collectAsState()
    val cargoTickets by viewModel.cargoTickets.collectAsState()
    val snackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() }

    LaunchedEffect(actionState) {
        if (actionState is TripDetailViewModel.ActionState.Error) {
            snackbarHostState.showSnackbar((actionState as TripDetailViewModel.ActionState.Error).message)
            viewModel.resetActionState()
        }
        if (actionState is TripDetailViewModel.ActionState.Success) {
            snackbarHostState.showSnackbar("Action réussie.")
            viewModel.resetActionState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détails du Trajet") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (uiState is TripDetailUiState.Success) {
                val trip = (uiState as TripDetailUiState.Success).trip
                if (trip.status == "in_progress") {
                    FloatingActionButton(
                        onClick = {
                            onNavigateToCreateTicket(
                                trip.id,
                                trip.passengerBasePrice,
                                trip.cargoSmallPrice,
                                trip.cargoMediumPrice,
                                trip.cargoLargePrice,
                                trip.currency
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text("+ Billet", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (val state = uiState) {
                is TripDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is TripDetailUiState.Error -> {
                    val msg = when (state.error) {
                        is TripException.NetworkUnavailable -> "Hors ligne. Vérifiez votre connexion."
                        else -> "Erreur de chargement."
                    }
                    Text(
                        msg,
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is TripDetailUiState.Success -> {
                    TripDetailContent(
                        trip = state.trip,
                        actionState = actionState,
                        passengerTickets = passengerTickets,
                        cargoTickets = cargoTickets,
                        onStartTrip = viewModel::startTrip,
                        onCompleteTrip = viewModel::completeTrip
                    )
                }
            }
        }
    }
}

@Composable
fun TripDetailContent(
    trip: TripDetailDto,
    actionState: TripDetailViewModel.ActionState,
    passengerTickets: List<PassengerTicketEntity>,
    cargoTickets: List<CargoTicketEntity>,
    onStartTrip: () -> Unit,
    onCompleteTrip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val formatter = androidx.compose.runtime.remember { DateTimeFormatter.ofPattern("dd MMM yyyy à HH:mm", Locale.FRANCE) }
        val departureStr = try {
            OffsetDateTime.parse(trip.departureDatetime).format(formatter)
        } catch (e: Exception) {
            trip.departureDatetime
        }

        // Header info
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TripStatusBadge(status = trip.status)
                Text("De : ${trip.originName}", style = MaterialTheme.typography.titleMedium)
                Text("À : ${trip.destinationName}", style = MaterialTheme.typography.titleMedium)
                Text("Départ : $departureStr", style = MaterialTheme.typography.bodyLarge)
                if (trip.arrivalDatetime != null) {
                    val arrivalStr = try {
                        OffsetDateTime.parse(trip.arrivalDatetime).format(formatter)
                    } catch (e: Exception) { trip.arrivalDatetime }
                    Text("Arrivée : $arrivalStr", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Conducteur : ${trip.conductorName}", style = MaterialTheme.typography.bodyLarge)
                Text("Bus (Immatriculation) : ${trip.busPlate}", style = MaterialTheme.typography.bodyLarge)
            }
        }
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Prix Passager : ${trip.passengerBasePrice} ${trip.currency}", style = MaterialTheme.typography.bodyMedium)
                Text("Colis Petit : ${trip.cargoSmallPrice} ${trip.currency}", style = MaterialTheme.typography.bodyMedium)
                Text("Colis Moyen : ${trip.cargoMediumPrice} ${trip.currency}", style = MaterialTheme.typography.bodyMedium)
                Text("Colis Grand : ${trip.cargoLargePrice} ${trip.currency}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (passengerTickets.isNotEmpty() || cargoTickets.isNotEmpty()) {
            Text("Billets créés hors ligne", style = MaterialTheme.typography.titleMedium)
            
            passengerTickets.forEach { pt ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(pt.ticketNumber, fontWeight = FontWeight.Bold)
                        Text("Passager: ${pt.passengerName}")
                        Text("Prix: ${pt.price / 100} ${pt.currency}")
                    }
                }
            }
            
            cargoTickets.forEach { ct ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(ct.ticketNumber, fontWeight = FontWeight.Bold)
                        Text("Expéditeur: ${ct.senderName} -> Destinataire: ${ct.receiverName}")
                        Text("Taille: ${ct.cargoTier} | Prix: ${ct.price / 100} ${ct.currency}")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp)) // Leave space for FAB

        // Action Buttons mapped purely by status
        val isLoading = actionState is TripDetailViewModel.ActionState.Loading
        if (trip.status == "scheduled") {
            Button(
                onClick = onStartTrip,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Démarrer le trajet", fontWeight = FontWeight.Bold)
                }
            }
        } else if (trip.status == "in_progress") {
            Button(
                onClick = onCompleteTrip,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Terminer le trajet", fontWeight = FontWeight.Bold)
                }
            }
        }
        // Cancel logic is strictly Office Staff only, hence purposefully omitted from Conductor UI
    }
}
