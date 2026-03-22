package com.souigat.mobile.ui.screens.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.ui.components.EmptyStatePanel
import com.souigat.mobile.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    viewModel: TripDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToCreateTicket: (Int) -> Unit = {},
    onNavigateToCreateExpense: (Int) -> Unit = {},
    onNavigateToSettlementSummary: (SettlementPreviewUiModel) -> Unit = {}
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
            is TripDetailViewModel.ActionState.Success -> {
                showCompleteDialog = false
                val preview = current.response.settlementPreview
                when {
                    preview != null -> {
                        snackbarHostState.showSnackbar("Trajet termine. Ouverture du recapitulatif de remise.")
                        onNavigateToSettlementSummary(viewModel.toSettlementPreviewUiModel(preview))
                    }
                    current.response.settlementPreviewError != null -> {
                        snackbarHostState.showSnackbar("Trajet termine. Le recapitulatif de remise est indisponible pour le moment.")
                    }
                    else -> {
                        snackbarHostState.showSnackbar("Mise a jour effectuee.")
                    }
                }
                viewModel.resetActionState()
            }
            else -> Unit
        }
    }

    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text("Confirmer la fin du trajet") },
            text = { Text("Cette action cloture le trajet et bloque les nouvelles ventes et depenses. Verifiez reellemnt avant de terminer.") },
            confirmButton = {
                Button(
                    onClick = viewModel::completeTrip,
                    enabled = actionState !is TripDetailViewModel.ActionState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA1A1A))
                ) { Text("Terminer") }
            },
            dismissButton = {
                Button(
                    onClick = { showCompleteDialog = false },
                    enabled = actionState !is TripDetailViewModel.ActionState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34D399))
                ) { Text("Annuler") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détail du trajet", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                ),
                modifier = Modifier.border(1.dp, Color(0xFFE2E5EA), RoundedCornerShape(0.dp))
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
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is TripDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp), contentAlignment = Alignment.Center) {
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
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item(key = "hero_trip_card") {
                        HeroTripCard(
                            origin = trip.origin,
                            destination = trip.destination,
                            busPlate = trip.busPlate,
                            statusLabel = trip.statusLabel,
                            departureLabel = trip.departureLabel
                        )
                    }

                    item(key = "conductor_team") {
                        DetailSectionCard(
                            title = "ÉQUIPE DE CONDUITE",
                            lines = listOf("Conducteur" to trip.conductorName, "Bus" to trip.busPlate)
                        )
                    }

                    item(key = "pricing") {
                        PriceSectionCard(priceLines = trip.priceLines)
                    }

                    item(key = "offline_title") {
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "ACTIVITÉ HORS-LIGNE",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )
                    }

                    item(key = "offline_tabs") {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Passagers", fontWeight = FontWeight.SemiBold) },
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Colis", fontWeight = FontWeight.SemiBold) },
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text("Depenses", fontWeight = FontWeight.SemiBold) },
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> {
                            if (passengerTickets.isEmpty()) {
                                item(key = "passenger_empty") {
                                    EmptyStatePanel(icon = Icons.Default.Person, title = "Aucun billet passager", message = "Les billets apparaitront ici.")
                                }
                            } else {
                                items(items = passengerTickets, key = { it.id }) { item -> OfflineActivityCard(item = item) }
                            }
                        }
                        1 -> {
                            if (cargoTickets.isEmpty()) {
                                item(key = "cargo_empty") {
                                    EmptyStatePanel(icon = Icons.Default.Inventory2, title = "Aucun colis hors ligne", message = "Les enregistrements apparaitront ici.")
                                }
                            } else {
                                items(items = cargoTickets, key = { it.id }) { item -> OfflineActivityCard(item = item) }
                            }
                        }
                        else -> {
                            if (expenses.isEmpty()) {
                                item(key = "expense_empty") {
                                    EmptyStatePanel(icon = Icons.Default.LocalAtm, title = "Aucune depense hors ligne", message = "Les depenses apparaitront ici.")
                                }
                            } else {
                                items(items = expenses, key = { it.id }) { item -> OfflineActivityCard(item = item) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroTripCard(origin: String, destination: String, busPlate: String, statusLabel: String, departureLabel: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFC3C5D7).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().align(Alignment.CenterVertically).background(Color(0xFF1A56DB)))
            Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text(
                            text = statusLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = origin.uppercase(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black))
                            Text(text = " → ", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primaryContainer)
                            Text(text = destination.uppercase(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black))
                        }
                    }
                    Text(
                        text = busPlate,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background).padding(12.dp)) {
                        Column {
                            Text(
                                text = "DÉPART PRÉVU",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = departureLabel,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSectionCard(title: String, lines: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(start = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFC3C5D7).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                lines.forEachIndexed { index, pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = pair.first, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(text = pair.second, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
                    }
                    if (index < lines.size - 1) {
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceSectionCard(priceLines: List<TripPriceLineUiModel>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "TARIFICATION",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(start = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFC3C5D7).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                priceLines.forEachIndexed { index, price ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = price.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            text = price.value,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End
                        )
                    }
                    if (index < priceLines.size - 1) {
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineActivityCard(item: OfflineActivityUiModel) {
    val toneColor = when (item.kind) {
        OfflineActivityKind.Passenger -> MaterialTheme.colorScheme.primary
        OfflineActivityKind.Cargo -> Color(0xFFEAB308) // Warning
        OfflineActivityKind.Expense -> Color(0xFFBA1A1A) // Error
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFC3C5D7).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(16.dp)
            .padding(bottom = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = item.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                StatusPill(text = item.amountLabel, containerColor = toneColor.copy(alpha = 0.1f), contentColor = toneColor)
            }
            Text(text = item.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = item.meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E5EA), RoundedCornerShape(0.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (trip.canCreateOfflineItems) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onCreateTicket,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f).height(48.dp).border(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Text("Billet", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onCreateExpense,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f).height(48.dp).border(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Text("Dépense", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (trip.canStartTrip) {
                Button(
                    onClick = onStartTrip,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().height(56.dp).shadow(4.dp, RoundedCornerShape(12.dp))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimaryContainer, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text("Démarrer le trajet", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            if (trip.canCompleteTrip) {
                Button(
                    onClick = onCompleteTrip,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA1A1A)),
                    modifier = Modifier.fillMaxWidth().height(56.dp).shadow(4.dp, RoundedCornerShape(12.dp))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Terminer", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
