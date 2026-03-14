package com.souigat.mobile.ui.screens.trips
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.ui.components.ConductorPanelSurface
import com.souigat.mobile.ui.components.EmptyStatePanel
import com.souigat.mobile.ui.components.StatusPill
import com.souigat.mobile.ui.components.TripSummaryCard
import com.souigat.mobile.ui.theme.ErrorRed
import com.souigat.mobile.ui.theme.Success
import com.souigat.mobile.ui.theme.Warning
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    viewModel: TripDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToCreateTicket: (Int) -> Unit = {},
    onNavigateToCreateExpense: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val passengerTickets by viewModel.passengerTickets.collectAsStateWithLifecycle()
    val cargoTickets by viewModel.cargoTickets.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showCompleteDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(actionState) {
        when (val current = actionState) {
            is TripDetailViewModel.ActionState.Error -> {
                showCompleteDialog = false
                snackbarHostState.showSnackbar(current.message)
                viewModel.resetActionState()
            }

            TripDetailViewModel.ActionState.Success -> {
                showCompleteDialog = false
                snackbarHostState.showSnackbar("Mise a jour effectuee.")
                viewModel.resetActionState()
            }

            else -> Unit
        }
    }

    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text("Confirmer la fin du trajet") },
            text = {
                Text("Cette action cloture le trajet et bloque les nouvelles ventes et depenses. Verifiez une derniere fois avant de terminer.")
            },
            confirmButton = {
                Button(
                    onClick = viewModel::completeTrip,
                    enabled = actionState !is TripDetailViewModel.ActionState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D))
                ) {
                    Text("Terminer")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showCompleteDialog = false },
                    enabled = actionState !is TripDetailViewModel.ActionState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Success)
                ) {
                    Text("Annuler")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail du trajet") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            val trip = (uiState as? TripDetailUiState.Success)?.trip
            if (trip != null) {
                TripDetailActionBar(
                    trip = trip,
                    isLoading = actionState is TripDetailViewModel.ActionState.Loading,
                    onCreateTicket = { onNavigateToCreateTicket(trip.id) },
                    onCreateExpense = { onNavigateToCreateExpense(trip.id) },
                    onStartTrip = viewModel::startTrip,
                    onCompleteTrip = { showCompleteDialog = true }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = uiState) {
            TripDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is TripDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStatePanel(
                        icon = Icons.Default.Inventory2,
                        title = "Trajet indisponible",
                        message = state.message,
                        primaryActionLabel = "Reessayer",
                        onPrimaryAction = viewModel::loadTripDetail,
                        secondaryActionLabel = "Retour",
                        onSecondaryAction = onNavigateBack
                    )
                }
            }

            is TripDetailUiState.Success -> {
                val trip = state.trip
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "trip_summary", contentType = "trip_summary") {
                        TripSummaryCard(
                            origin = trip.origin,
                            destination = trip.destination,
                            busPlate = trip.busPlate,
                            departureLabel = trip.departureLabel,
                            statusLabel = trip.statusLabel,
                            supportingLabel = trip.arrivalLabel ?: "Arrivee non renseignee"
                        )
                    }

                    item(key = "operator", contentType = "section_card") {
                        DetailSectionCard(
                            title = "Equipe de conduite",
                            lines = listOf(
                                "Conducteur" to trip.conductorName,
                                "Bus" to trip.busPlate
                            )
                        )
                    }

                    item(key = "pricing", contentType = "section_card") {
                        PriceSectionCard(priceLines = trip.priceLines)
                    }

                    item(key = "offline_title", contentType = "section_header") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Activite hors ligne",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Billets, colis et depenses crees localement avant synchronisation serveur.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    item(key = "offline_tabs", contentType = "tab_row") {
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Passagers") },
                                icon = { Icon(Icons.Default.Person, contentDescription = null) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Colis") },
                                icon = { Icon(Icons.Default.Inventory2, contentDescription = null) }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text("Depenses") },
                                icon = { Icon(Icons.Default.LocalAtm, contentDescription = null) }
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> {
                            if (passengerTickets.isEmpty()) {
                                item(key = "passenger_empty") {
                                    EmptyStatePanel(
                                        icon = Icons.Default.Person,
                                        title = "Aucun billet passager",
                                        message = "Les billets crees hors ligne apparaitront ici."
                                    )
                                }
                            } else {
                                items(
                                    items = passengerTickets,
                                    key = { it.id },
                                    contentType = { "offline_activity" }
                                ) { item ->
                                    OfflineActivityCard(item = item)
                                }
                            }
                        }

                        1 -> {
                            if (cargoTickets.isEmpty()) {
                                item(key = "cargo_empty") {
                                    EmptyStatePanel(
                                        icon = Icons.Default.Inventory2,
                                        title = "Aucun colis hors ligne",
                                        message = "Les enregistrements colis apparaitront ici."
                                    )
                                }
                            } else {
                                items(
                                    items = cargoTickets,
                                    key = { it.id },
                                    contentType = { "offline_activity" }
                                ) { item ->
                                    OfflineActivityCard(item = item)
                                }
                            }
                        }

                        else -> {
                            if (expenses.isEmpty()) {
                                item(key = "expense_empty") {
                                    EmptyStatePanel(
                                        icon = Icons.Default.LocalAtm,
                                        title = "Aucune depense hors ligne",
                                        message = "Les depenses creees sur ce trajet apparaitront ici."
                                    )
                                }
                            } else {
                                items(
                                    items = expenses,
                                    key = { it.id },
                                    contentType = { "offline_activity" }
                                ) { item ->
                                    OfflineActivityCard(item = item)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    lines: List<Pair<String, String>>
) {
    ConductorPanelSurface(shape = MaterialTheme.shapes.extraLarge) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            lines.forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceSectionCard(priceLines: List<TripPriceLineUiModel>) {
    ConductorPanelSurface(shape = MaterialTheme.shapes.extraLarge) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tarification",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            priceLines.forEach { price ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = price.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = price.value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineActivityCard(item: OfflineActivityUiModel) {
    val tone = when (item.kind) {
        OfflineActivityKind.Passenger -> MaterialTheme.colorScheme.primary
        OfflineActivityKind.Cargo -> Warning
        OfflineActivityKind.Expense -> ErrorRed
    }

    ConductorPanelSurface(shape = MaterialTheme.shapes.extraLarge) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                StatusPill(
                    text = item.amountLabel,
                    containerColor = tone.copy(alpha = 0.14f),
                    contentColor = tone
                )
            }
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = item.meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TripDetailActionBar(
    trip: TripDetailUiModel,
    isLoading: Boolean,
    onCreateTicket: () -> Unit,
    onCreateExpense: () -> Unit,
    onStartTrip: () -> Unit,
    onCompleteTrip: () -> Unit
) {
    ConductorPanelSurface(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (trip.canCreateOfflineItems) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCreateTicket,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Billet")
                    }
                    OutlinedButton(
                        onClick = onCreateExpense,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Depense")
                    }
                }
            }

            when {
                trip.canStartTrip -> {
                    Button(
                        onClick = onStartTrip,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(vertical = 2.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Demarrer")
                        }
                    }
                }

                trip.canCompleteTrip -> {
                    Button(
                        onClick = onCompleteTrip,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Terminer")
                        }
                    }
                }
            }
        }
    }
}
