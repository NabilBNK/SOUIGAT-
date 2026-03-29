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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.ui.components.StitchCard
import com.souigat.mobile.ui.components.StitchDivider
import com.souigat.mobile.ui.components.StitchMonoText
import com.souigat.mobile.ui.components.StitchOutlineButton
import com.souigat.mobile.ui.components.StitchPill
import com.souigat.mobile.ui.components.StitchPrimaryButton
import com.souigat.mobile.ui.components.StitchSectionLabel
import com.souigat.mobile.ui.theme.ErrorRed
import com.souigat.mobile.ui.theme.Success

@Composable
fun TripDetailScreen(
    viewModel: TripDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToCreateTicket: (Long) -> Unit = {},
    onNavigateToCreateCargo: (Long) -> Unit = {},
    onNavigateToCreateExpense: (Long) -> Unit = {},
    onNavigateToSettlementSummary: (SettlementPreviewUiModel) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val cargoActionState by viewModel.cargoActionState.collectAsStateWithLifecycle()
    val passengerTicketCount by viewModel.passengerTicketCount.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    var selectedOfflineSection by rememberSaveable { mutableIntStateOf(0) }
    var showCompleteDialog by rememberSaveable { mutableStateOf(false) }
    var showDeliverAllDialog by rememberSaveable { mutableStateOf(false) }
    var selectedCargoForDelivery by remember { mutableStateOf<OfflineActivityUiModel?>(null) }
    val isBulkModeActive = showDeliverAllDialog || cargoActionState is TripDetailViewModel.CargoActionState.Loading

    LaunchedEffect(actionState) {
        when (val current = actionState) {
            is TripDetailViewModel.ActionState.Error -> {
                showCompleteDialog = false
                snackbarHostState.showSnackbar(current.message)
                viewModel.resetActionState()
            }
            is TripDetailViewModel.ActionState.Success -> {
                showCompleteDialog = false
                val preview = current.response.completionRecap
                when {
                    preview != null -> {
                        snackbarHostState.showSnackbar("Trajet termine. Ouverture du recapitulatif.")
                        onNavigateToSettlementSummary(viewModel.toSettlementPreviewUiModel(preview))
                    }
                    else -> snackbarHostState.showSnackbar("Mise a jour effectuee.")
                }
                viewModel.resetActionState()
            }
            else -> Unit
        }
    }

    LaunchedEffect(cargoActionState) {
        when (val current = cargoActionState) {
            is TripDetailViewModel.CargoActionState.Error -> {
                snackbarHostState.showSnackbar(current.message)
                viewModel.resetCargoActionState()
            }
            is TripDetailViewModel.CargoActionState.Success -> {
                snackbarHostState.showSnackbar(current.message)
                viewModel.resetCargoActionState()
            }
            else -> Unit
        }
    }

    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text("Confirmer la fin du trajet") },
            text = { Text("Cette action cloture le trajet et bloque les nouvelles ventes et depenses.") },
            confirmButton = {
                Button(
                    onClick = viewModel::completeTrip,
                    enabled = actionState !is TripDetailViewModel.ActionState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Terminer")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showCompleteDialog = false },
                    enabled = actionState !is TripDetailViewModel.ActionState.Loading
                ) {
                    Text("Annuler")
                }
            }
        )
    }

    val cargoToDeliver = selectedCargoForDelivery
    if (cargoToDeliver != null && !showDeliverAllDialog) {
        AlertDialog(
            onDismissRequest = { selectedCargoForDelivery = null },
            title = { Text("Livraison du colis") },
            text = {
                Text("Choisissez la cible de livraison pour ${cargoToDeliver.title}.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val localId = cargoToDeliver.localId
                        if (localId != null) {
                            viewModel.deliverCargo(localId, CargoDeliveryTarget.Receiver)
                        }
                        selectedCargoForDelivery = null
                    },
                    enabled = cargoActionState !is TripDetailViewModel.CargoActionState.Loading
                ) {
                    Text("Destinataire")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val localId = cargoToDeliver.localId
                            if (localId != null) {
                                viewModel.deliverCargo(localId, CargoDeliveryTarget.Agency)
                            }
                            selectedCargoForDelivery = null
                        },
                        enabled = cargoActionState !is TripDetailViewModel.CargoActionState.Loading
                    ) {
                        Text("Agence")
                    }
                    TextButton(
                        onClick = { selectedCargoForDelivery = null },
                        enabled = cargoActionState !is TripDetailViewModel.CargoActionState.Loading
                    ) {
                        Text("Annuler")
                    }
                }
            }
        )
    }

    if (showDeliverAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeliverAllDialog = false },
            title = { Text("Transferer tous vers agence") },
            text = { Text("Tous les colis ouverts de ce trajet seront transferes vers l'agence d'arrivee.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeliverAllDialog = false
                        viewModel.handoverAllCargoToAgency()
                    },
                    enabled = cargoActionState !is TripDetailViewModel.CargoActionState.Loading
                ) {
                    Text("Confirmer")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeliverAllDialog = false },
                    enabled = cargoActionState !is TripDetailViewModel.CargoActionState.Loading
                ) {
                    Text("Annuler")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Text(
                    text = "Detail du trajet",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF12392C)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        },
        bottomBar = {
            val trip = (uiState as? TripDetailUiState.Success)?.trip
            if (trip != null) {
                TripDetailActionBar(
                    trip = trip,
                    isLoading = actionState is TripDetailViewModel.ActionState.Loading,
                    onCreateTicket = { onNavigateToCreateTicket(trip.id) },
                    onCreateCargo = { onNavigateToCreateCargo(trip.id) },
                    onCreateExpense = { onNavigateToCreateExpense(trip.id) },
                    canCreateCargoItems = trip.canCreateCargoItems,
                    onStartTrip = viewModel::startTrip,
                    onCompleteTrip = { showCompleteDialog = true }
                )
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            TripDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primaryContainer)
                }
            }

            is TripDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    StitchCard(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Trajet indisponible",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StitchPrimaryButton(
                            label = "Reessayer",
                            onClick = viewModel::loadTripDetail,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            is TripDetailUiState.Success -> {
                val trip = state.trip
                val visiblePassengerTickets = if (selectedSection == 1 && selectedOfflineSection == 0) {
                    viewModel.passengerTickets.collectAsStateWithLifecycle().value
                } else {
                    emptyList()
                }
                val visibleCargoTickets = if (selectedSection == 1 && selectedOfflineSection == 1) {
                    viewModel.cargoTickets.collectAsStateWithLifecycle().value
                } else {
                    emptyList()
                }
                val visibleExpenses = if (selectedSection == 1 && selectedOfflineSection == 2) {
                    viewModel.expenses.collectAsStateWithLifecycle().value
                } else {
                    emptyList()
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 170.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item("hero_card") {
                        HeroTripCard(
                            trip = trip,
                            passengerCount = passengerTicketCount
                        )
                    }

                    item("section_tabs") {
                        TripDetailTabs(
                            selectedIndex = selectedSection,
                            onSelected = { selectedSection = it }
                        )
                    }

                    when (selectedSection) {
                        0 -> {
                            item("team_card") {
                                DetailSectionCard(
                                    title = "Equipe de conduite",
                                    lines = listOf(
                                        "Chauffeur principal" to trip.conductorName,
                                        "Bus" to trip.busPlate,
                                        "Statut" to trip.statusLabel
                                    )
                                )
                            }

                            item("support_tiles") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    MiniInfoTile(
                                        title = "Passagers",
                                        value = "$passengerTicketCount/50",
                                        modifier = Modifier.weight(1f)
                                    )
                                    MiniInfoTile(
                                        title = "Tarif standard",
                                        value = trip.priceLines.firstOrNull()?.value ?: "--",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        1 -> {
                            item("offline_switcher") {
                                OfflineSegmentedControl(
                                    selectedIndex = selectedOfflineSection,
                                    onSelected = { selectedOfflineSection = it }
                                )
                            }

                            when (selectedOfflineSection) {
                                0 -> {
                                    if (visiblePassengerTickets.isEmpty()) {
                                        item("offline_empty_passengers") {
                                            EmptyOfflineCard("Aucun billet passager hors ligne.")
                                        }
                                    } else {
                                        items(visiblePassengerTickets, key = { it.id }) { item ->
                                            OfflineActivityCard(item)
                                        }
                                    }
                                }

                                1 -> {
                                    if (visibleCargoTickets.isEmpty()) {
                                        item("offline_empty_cargo") {
                                            EmptyOfflineCard("Aucun billet colis hors ligne.")
                                        }
                                    } else {
                                        val openCargoCount = visibleCargoTickets.count { it.canHandoverToAgency }
                                        item("cargo_bulk_actions") {
                                            StitchCard(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                contentPadding = PaddingValues(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "$openCargoCount colis ouvert(s)",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                    StitchOutlineButton(
                                                        label = "Transferer tous vers agence",
                                                        onClick = {
                                                            selectedCargoForDelivery = null
                                                            showDeliverAllDialog = true
                                                        },
                                                        enabled = openCargoCount > 0
                                                            && cargoActionState !is TripDetailViewModel.CargoActionState.Loading,
                                                    )
                                                }
                                            }
                                        }
                                        items(visibleCargoTickets, key = { it.id }) { item ->
                                            CargoOfflineActivityCard(
                                                item = item,
                                                isLoading = cargoActionState is TripDetailViewModel.CargoActionState.Loading,
                                                showDeliverAction = !isBulkModeActive,
                                                onDeliverClick = {
                                                    if (!isBulkModeActive) {
                                                        selectedCargoForDelivery = item
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                else -> {
                                    if (visibleExpenses.isEmpty()) {
                                        item("offline_empty_expenses") {
                                            EmptyOfflineCard("Aucune depense hors ligne.")
                                        }
                                    } else {
                                        items(visibleExpenses, key = { it.id }) { item ->
                                            OfflineActivityCard(item)
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            item("pricing_card") {
                                PriceSectionCard(priceLines = trip.priceLines)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroTripCard(trip: TripDetailUiModel, passengerCount: Int) {
    StitchCard(contentPadding = PaddingValues(0.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .heightIn(min = 170.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StitchSectionLabel("Ligne active")
                        Text(
                            text = "${trip.origin.uppercase()} -> ${trip.destination.uppercase()}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    StitchPill(
                        text = trip.busPlate,
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiniInfoTile(
                        title = "Depart prevu",
                        value = trip.departureLabel.takeLast(5),
                        modifier = Modifier.weight(1f)
                    )
                    MiniInfoTile(
                        title = "Passagers",
                        value = "$passengerCount/50",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TripDetailTabs(selectedIndex: Int, onSelected: (Int) -> Unit) {
    val labels = listOf("Informations", "Activite hors-ligne", "Tarification")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        labels.forEachIndexed { index, label ->
            Column(
                modifier = Modifier
                    .clickable { onSelected(index) }
                    .padding(bottom = 4.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selectedIndex == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(if (selectedIndex == index) 86.dp else 0.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }
        }
    }
}

@Composable
private fun DetailSectionCard(title: String, lines: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StitchSectionLabel(title)
        StitchCard {
            lines.forEachIndexed { index, line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = line.first,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = line.second,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End
                    )
                }
                if (index < lines.lastIndex) {
                    StitchDivider()
                }
            }
        }
    }
}

@Composable
private fun MiniInfoTile(title: String, value: String, modifier: Modifier = Modifier) {
    StitchCard(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        StitchSectionLabel(title)
        StitchMonoText(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun OfflineSegmentedControl(selectedIndex: Int, onSelected: (Int) -> Unit) {
    val labels = listOf("Passagers", "Colis", "Depenses")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selectedIndex == index) {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (selectedIndex == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PriceSectionCard(priceLines: List<TripPriceLineUiModel>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StitchSectionLabel("Tarification")
        StitchCard {
            priceLines.forEachIndexed { index, line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = line.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StitchMonoText(
                        text = line.value,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (index < priceLines.lastIndex) {
                    StitchDivider()
                }
            }
        }
    }
}

@Composable
private fun EmptyOfflineCard(message: String) {
    StitchCard {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OfflineActivityCard(item: OfflineActivityUiModel) {
    val tone = when (item.kind) {
        OfflineActivityKind.Passenger -> Success
        OfflineActivityKind.Cargo -> MaterialTheme.colorScheme.primaryContainer
        OfflineActivityKind.Expense -> ErrorRed
    }

    StitchCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            StitchPill(
                text = item.amountLabel,
                containerColor = tone.copy(alpha = 0.12f),
                contentColor = tone
            )
        }
        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = item.meta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CargoOfflineActivityCard(
    item: OfflineActivityUiModel,
    isLoading: Boolean,
    showDeliverAction: Boolean,
    onDeliverClick: () -> Unit,
) {
    val statusTone = when (item.status) {
        "delivered" -> Success
        "arrived" -> MaterialTheme.colorScheme.primaryContainer
        "in_transit", "loaded", "created" -> Color(0xFF9A6A00)
        "cancelled" -> ErrorRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusLabel = item.statusLabel ?: item.status ?: "Inconnu"
    val canDeliver = item.canDeliverToReceiver || item.canHandoverToAgency

    StitchCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            StitchPill(
                text = item.amountLabel,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primaryContainer
            )
        }
        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = item.meta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StitchPill(
                text = statusLabel,
                containerColor = statusTone.copy(alpha = 0.14f),
                contentColor = statusTone
            )
            if (showDeliverAction && canDeliver && item.localId != null) {
                StitchOutlineButton(
                    label = "Deliver",
                    onClick = onDeliverClick,
                    enabled = !isLoading
                )
            }
        }
    }
}

@Composable
private fun TripDetailActionBar(
    trip: TripDetailUiModel,
    isLoading: Boolean,
    onCreateTicket: () -> Unit,
    onCreateCargo: () -> Unit,
    onCreateExpense: () -> Unit,
    canCreateCargoItems: Boolean,
    onStartTrip: () -> Unit,
    onCompleteTrip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (trip.canCreateOfflineItems || canCreateCargoItems) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (trip.canCreateOfflineItems) {
                        StitchOutlineButton("Billet", onCreateTicket, modifier = Modifier.weight(1f))
                    }
                    if (canCreateCargoItems) {
                        StitchOutlineButton("Colis", onCreateCargo, modifier = Modifier.weight(1f))
                    }
                    if (trip.canCreateOfflineItems) {
                        StitchOutlineButton("Depense", onCreateExpense, modifier = Modifier.weight(1f))
                    }
                }
            }

            if (trip.canStartTrip) {
                StitchPrimaryButton(
                    label = if (isLoading) "Chargement..." else "Demarrer le trajet",
                    onClick = onStartTrip,
                    leadingIcon = Icons.Default.PlayCircle,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )
            }

            if (trip.canCompleteTrip) {
                Button(
                    onClick = onCompleteTrip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Terminer le trajet", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
