package com.souigat.mobile.ui.screens.tickets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.data.local.TicketFormDraft
import com.souigat.mobile.R
import com.souigat.mobile.ui.components.StitchCard
import com.souigat.mobile.ui.components.StitchMonoText
import com.souigat.mobile.ui.components.StitchSectionLabel
import com.souigat.mobile.ui.model.CargoTierPriceUiModel
import com.souigat.mobile.ui.model.TripFormHeaderUiModel
import com.souigat.mobile.util.formatCompact
import com.souigat.mobile.util.formatCurrency
import com.souigat.mobile.util.parseCurrencyInput
import kotlinx.coroutines.delay

@Composable
fun CreateTicketScreen(
    viewModel: CreateTicketViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    startWithCargo: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val draftState by viewModel.draftState.collectAsStateWithLifecycle()
    val ticketPreviewEnabled by viewModel.ticketPreviewEnabled.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by rememberSaveable { mutableIntStateOf(if (startWithCargo) 1 else 0) }
    var passengerCount by rememberSaveable { mutableIntStateOf(1) }
    var passengerPriceInput by rememberSaveable { mutableStateOf("") }
    var seatNumber by rememberSaveable { mutableStateOf("") }
    var boardingPoint by rememberSaveable { mutableStateOf("") }
    var alightingPoint by rememberSaveable { mutableStateOf("") }
    var passengerPaymentSource by rememberSaveable { mutableStateOf("cash") }

    var senderName by rememberSaveable { mutableStateOf("") }
    var senderPhone by rememberSaveable { mutableStateOf("") }
    var receiverName by rememberSaveable { mutableStateOf("") }
    var receiverPhone by rememberSaveable { mutableStateOf("") }
    var cargoDescription by rememberSaveable { mutableStateOf("") }
    var cargoTier by rememberSaveable { mutableStateOf("") }
    var cargoPaymentSource by rememberSaveable { mutableStateOf("prepaid") }
    var hasHydratedDraft by rememberSaveable { mutableStateOf(false) }
    var showCreateConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showTicketPreviewDialog by rememberSaveable { mutableStateOf(false) }
    var ticketPreviewModel by remember { mutableStateOf<PassengerTicketPreviewModel?>(null) }

    LaunchedEffect(draftState, hasHydratedDraft) {
        if (hasHydratedDraft) return@LaunchedEffect
        selectedTab = draftState.selectedTab.coerceIn(0, 1)
        passengerCount = draftState.passengerCount.coerceIn(1, 50)
        passengerPriceInput = draftState.passengerPriceInput
        seatNumber = draftState.seatNumber
        boardingPoint = draftState.boardingPoint
        alightingPoint = draftState.alightingPoint
        passengerPaymentSource = draftState.passengerPaymentSource
        senderName = draftState.senderName
        senderPhone = draftState.senderPhone
        receiverName = draftState.receiverName
        receiverPhone = draftState.receiverPhone
        cargoDescription = draftState.cargoDescription
        cargoTier = draftState.cargoTier
        cargoPaymentSource = draftState.cargoPaymentSource
        hasHydratedDraft = true
    }

    LaunchedEffect(
        hasHydratedDraft,
        selectedTab,
        passengerCount,
        passengerPriceInput,
        seatNumber,
        boardingPoint,
        alightingPoint,
        passengerPaymentSource,
        senderName,
        senderPhone,
        receiverName,
        receiverPhone,
        cargoDescription,
        cargoTier,
        cargoPaymentSource
    ) {
        if (!hasHydratedDraft) return@LaunchedEffect
        delay(350)
        viewModel.persistDraft(
            TicketFormDraft(
                selectedTab = selectedTab,
                passengerCount = passengerCount,
                passengerPriceInput = passengerPriceInput,
                seatNumber = seatNumber,
                boardingPoint = boardingPoint,
                alightingPoint = alightingPoint,
                passengerPaymentSource = passengerPaymentSource,
                senderName = senderName,
                senderPhone = senderPhone,
                receiverName = receiverName,
                receiverPhone = receiverPhone,
                cargoDescription = cargoDescription,
                cargoTier = cargoTier,
                cargoPaymentSource = cargoPaymentSource
            )
        )
    }

    LaunchedEffect(formState) {
        val ready = formState as? TicketFormHeaderState.Ready ?: return@LaunchedEffect
        val normalizedStops = normalizeStops(ready.routeStops)
        val boardingOptions = boardingStopOptions(normalizedStops)
        if (cargoTier.isBlank() || ready.cargoTierPrices.none { it.tier == cargoTier }) {
            cargoTier = ready.cargoTierPrices.firstOrNull()?.tier.orEmpty()
        }
        if (boardingPoint.isBlank() || boardingOptions.none { it.equals(boardingPoint.trim(), ignoreCase = true) }) {
            boardingPoint = boardingOptions.firstOrNull() ?: ready.header.origin
        }
        if (alightingPoint.isBlank() || !isForwardPathSelected(normalizedStops, boardingPoint, alightingPoint)) {
            alightingPoint = forwardDestinationOptions(normalizedStops, boardingPoint).firstOrNull()
                ?: ready.header.destination
        }
        val fare = computeForwardFare(normalizedStops, ready.routeSegments, boardingPoint, alightingPoint)
            ?: ready.passengerBasePriceCentimes
        passengerPriceInput = formatCompact(fare)
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is CreateTicketUiState.Success -> {
                if (ticketPreviewEnabled && state.preview != null) {
                    ticketPreviewModel = state.preview
                    showTicketPreviewDialog = true
                    viewModel.resetState()
                } else {
                    viewModel.resetState()
                    onNavigateBack()
                }
            }
            is CreateTicketUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    if (showTicketPreviewDialog && ticketPreviewModel != null) {
        AlertDialog(
            onDismissRequest = {
                showTicketPreviewDialog = false
                ticketPreviewModel = null
                onNavigateBack()
            },
            title = { Text("Apercu du billet") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    PassengerTicketPreviewCard(model = ticketPreviewModel!!)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTicketPreviewDialog = false
                        ticketPreviewModel = null
                        onNavigateBack()
                    }
                ) {
                    Text("Fermer")
                }
            },
        )
    }

    val isLoading = uiState is CreateTicketUiState.Loading
    val readyState = formState as? TicketFormHeaderState.Ready
    val passengerPriceCentimes by remember(passengerPriceInput, readyState, boardingPoint, alightingPoint) {
        derivedStateOf {
            val ready = readyState
            if (ready != null) {
                computeForwardFare(
                    routeStops = ready.routeStops,
                    routeSegments = ready.routeSegments,
                    boardingPoint = boardingPoint,
                    alightingPoint = alightingPoint,
                ) ?: ready.passengerBasePriceCentimes
            } else {
                parseCurrencyInput(passengerPriceInput) ?: 0L
            }
        }
    }
    val passengerTotal = passengerPriceCentimes * passengerCount
    val cargoPrice = readyState?.cargoTierPrices?.firstOrNull { it.tier == cargoTier }?.valueCentimes ?: 0L
    val footerAmount = if (selectedTab == 0) passengerTotal else cargoPrice
    val footerCurrency = readyState?.header?.currency ?: "DZD"
    val canSubmit = if (selectedTab == 0) {
        passengerPriceCentimes > 0 &&
            passengerCount in 1..50 &&
            readyState?.let { isForwardPathSelected(it.routeStops, boardingPoint, alightingPoint) } == true
    } else {
        senderName.isNotBlank() && receiverName.isNotBlank() && cargoPrice > 0
    }

    if (showCreateConfirmDialog && readyState != null) {
        val passengerSummary = "${passengerCount} billet(s), ${boardingPoint.ifBlank { readyState.header.origin }} -> ${alightingPoint.ifBlank { readyState.header.destination }}, total ${formatCurrency(passengerTotal, readyState.header.currency)}."
        val selectedCargoTier = readyState.cargoTierPrices.firstOrNull { it.tier == cargoTier }
        val cargoTierLabel = selectedCargoTier?.label ?: cargoTier
        val cargoSummary = "${senderName.ifBlank { "Expediteur" }} -> ${receiverName.ifBlank { "Destinataire" }}, ${cargoTierLabel}, ${formatCurrency(cargoPrice, readyState.header.currency)}."

        AlertDialog(
            onDismissRequest = {
                if (!isLoading) {
                    showCreateConfirmDialog = false
                }
            },
            title = { Text("Confirmer la creation") },
            text = {
                Text(
                    if (selectedTab == 0) {
                        "Voulez-vous creer ces billets passager ?\n$passengerSummary"
                    } else {
                        "Voulez-vous creer ce billet colis ?\n$cargoSummary"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateConfirmDialog = false
                        if (selectedTab == 0) {
                            viewModel.createPassengerTicketBatch(
                                count = passengerCount,
                                paymentSource = passengerPaymentSource,
                                seatNumber = seatNumber,
                                boardingPoint = boardingPoint,
                                alightingPoint = alightingPoint
                            )
                        } else {
                            viewModel.createCargoTicket(
                                senderName = senderName,
                                senderPhone = senderPhone,
                                receiverName = receiverName,
                                receiverPhone = receiverPhone,
                                cargoTier = cargoTier,
                                description = cargoDescription,
                                paymentSource = cargoPaymentSource
                            )
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text("Oui, creer")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateConfirmDialog = false },
                    enabled = !isLoading
                ) {
                    Text("Annuler")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TicketTopBar(
                onNavigateBack = onNavigateBack,
                onRetryLookup = viewModel::retryLookup
            )
        },
        bottomBar = {
            if (readyState != null) {
                TicketFooterBar(
                    totalLabel = formatTicketTotal(footerAmount, footerCurrency),
                    buttonLabel = "Creer billet",
                    isLoading = isLoading,
                    enabled = canSubmit,
                    onSubmit = {
                        showCreateConfirmDialog = true
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when (val state = formState) {
            TicketFormHeaderState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primaryContainer)
                }
            }
            is TicketFormHeaderState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    StitchCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Trajet introuvable",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = viewModel::retryLookup,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reessayer")
                        }
                    }
                }
            }
            is TicketFormHeaderState.Ready -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 132.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item("summary") { TicketTripSummary(header = state.header) }
                    item("tabs") {
                        TicketTabSelector(
                            selectedTab = selectedTab,
                            onSelectedTabChange = { selectedTab = it }
                        )
                    }
                    item("content") {
                        if (selectedTab == 0) {
                            PassengerTicketForm(
                                stopOptions = state.routeStops,
                                passengerPriceLabel = formatCurrency(passengerPriceCentimes, state.header.currency),
                                suggestedPriceLabel = formatCurrency(state.passengerBasePriceCentimes, state.header.currency),
                                passengerCount = passengerCount,
                                onIncreaseCount = { if (passengerCount < 50) passengerCount += 1 },
                                onDecreaseCount = { if (passengerCount > 1) passengerCount -= 1 },
                                seatNumber = seatNumber,
                                onSeatNumberChange = { seatNumber = it.filter(Char::isDigit) },
                                boardingPoint = boardingPoint,
                                onBoardingPointChange = {
                                    boardingPoint = it
                                    if (!isForwardPathSelected(state.routeStops, it, alightingPoint)) {
                                        val nextStop = forwardDestinationOptions(state.routeStops, it).firstOrNull()
                                        if (nextStop != null) {
                                            alightingPoint = nextStop
                                        }
                                    }
                                },
                                alightingPoint = alightingPoint,
                                onAlightingPointChange = { alightingPoint = it },
                                paymentSource = passengerPaymentSource,
                                onPaymentSourceChange = { passengerPaymentSource = it },
                                isLoading = isLoading
                            )
                        } else {
                            CargoTicketForm(
                                cargoTierPrices = state.cargoTierPrices,
                                selectedTier = cargoTier,
                                onTierSelected = { cargoTier = it },
                                senderName = senderName,
                                onSenderNameChange = { senderName = it },
                                senderPhone = senderPhone,
                                onSenderPhoneChange = {
                                    senderPhone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' }
                                },
                                receiverName = receiverName,
                                onReceiverNameChange = { receiverName = it },
                                receiverPhone = receiverPhone,
                                onReceiverPhoneChange = {
                                    receiverPhone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' }
                                },
                                description = cargoDescription,
                                onDescriptionChange = { if (it.length <= 300) cargoDescription = it },
                                paymentSource = cargoPaymentSource,
                                onPaymentSourceChange = { cargoPaymentSource = it },
                                isLoading = isLoading
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketTopBar(
    onNavigateBack: () -> Unit,
    onRetryLookup: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
        }
        Text(
            text = "Nouveau billet",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF7FA06B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.souigat_logo_no_background),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onRetryLookup) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Synchroniser",
                tint = MaterialTheme.colorScheme.primaryContainer
            )
        }
    }
}

@Composable
private fun TicketTripSummary(header: TripFormHeaderUiModel) {
    StitchCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StitchSectionLabel("Ligne actuelle")
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = header.statusLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }
                Text(
                    text = header.origin,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = header.destination,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StitchSectionLabel("Bus no")
                StitchMonoText(
                    text = header.busPlate,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    }
}

@Composable
private fun TicketTabSelector(
    selectedTab: Int,
    onSelectedTabChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        TicketTabButton(
            title = "Passager",
            selected = selectedTab == 0,
            onClick = { onSelectedTabChange(0) },
            modifier = Modifier.weight(1f)
        )
        TicketTabButton(
            title = "Colis",
            selected = selectedTab == 1,
            onClick = { onSelectedTabChange(1) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TicketTabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun PassengerTicketForm(
    stopOptions: List<String>,
    passengerPriceLabel: String,
    suggestedPriceLabel: String,
    passengerCount: Int,
    onIncreaseCount: () -> Unit,
    onDecreaseCount: () -> Unit,
    seatNumber: String,
    onSeatNumberChange: (String) -> Unit,
    boardingPoint: String,
    onBoardingPointChange: (String) -> Unit,
    alightingPoint: String,
    onAlightingPointChange: (String) -> Unit,
    paymentSource: String,
    onPaymentSourceChange: (String) -> Unit,
    isLoading: Boolean
) {
    val normalizedStops = remember(stopOptions) {
        normalizeStops(stopOptions)
    }
    val boardingOptions = remember(normalizedStops) {
        boardingStopOptions(normalizedStops)
    }
    val destinationOptions = remember(normalizedStops, boardingPoint) {
        forwardDestinationOptions(normalizedStops, boardingPoint)
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StitchSectionLabel("Nombre de places")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CountActionButton(
                    icon = Icons.Default.Remove,
                    selected = false,
                    enabled = !isLoading && passengerCount > 1,
                    onClick = onDecreaseCount
                )
                StitchMonoText(
                    text = passengerCount.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
                CountActionButton(
                    icon = Icons.Default.Add,
                    selected = true,
                    enabled = !isLoading && passengerCount < 50,
                    onClick = onIncreaseCount
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StitchSectionLabel("Prix unitaire")
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.18f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Tarif conseille",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            OutlinedTextField(
                value = passengerPriceLabel.substringBefore(" "),
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                trailingIcon = {
                    StitchMonoText(
                        text = "DZD",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                placeholder = {
                    StitchMonoText(
                        text = suggestedPriceLabel.substringBefore(" "),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = stitchFieldColors()
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StitchSectionLabel("Arret de montee")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                boardingOptions.forEach { stop ->
                    val selected = stop == boardingPoint
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest
                            )
                            .border(
                                width = if (selected) 0.dp else 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .clickable(enabled = !isLoading) { onBoardingPointChange(stop) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stop,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StitchSectionLabel("Arret de destination")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                destinationOptions.forEach { stop ->
                    val selected = stop == alightingPoint
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest
                            )
                            .border(
                                width = if (selected) 0.dp else 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .clickable(enabled = !isLoading) { onAlightingPointChange(stop) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stop,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
            if (destinationOptions.isEmpty()) {
                Text(
                    text = "Aucune destination disponible apres ce point de montee.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        StitchCard(modifier = Modifier.fillMaxWidth()) {
            StitchSectionLabel("Informations supplementaires")
            TicketField(
                label = "Numero de siege",
                value = seatNumber,
                onValueChange = onSeatNumberChange,
                placeholder = "Optionnel",
                enabled = !isLoading,
                keyboardType = KeyboardType.Number
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StitchSectionLabel("Mode de paiement")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaymentChoiceCard(
                    title = "Especes",
                    icon = Icons.Default.Payments,
                    selected = paymentSource == "cash",
                    enabled = !isLoading,
                    onClick = { onPaymentSourceChange("cash") },
                    modifier = Modifier.weight(1f)
                )
                PaymentChoiceCard(
                    title = "Prepaye",
                    icon = Icons.Default.CreditCard,
                    selected = paymentSource == "prepaid",
                    enabled = !isLoading,
                    onClick = { onPaymentSourceChange("prepaid") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CargoTicketForm(
    cargoTierPrices: List<CargoTierPriceUiModel>,
    selectedTier: String,
    onTierSelected: (String) -> Unit,
    senderName: String,
    onSenderNameChange: (String) -> Unit,
    senderPhone: String,
    onSenderPhoneChange: (String) -> Unit,
    receiverName: String,
    onReceiverNameChange: (String) -> Unit,
    receiverPhone: String,
    onReceiverPhoneChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    paymentSource: String,
    onPaymentSourceChange: (String) -> Unit,
    isLoading: Boolean
) {
    var showDetails by rememberSaveable { mutableStateOf(description.isNotBlank()) }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StitchSectionLabel("Format du colis")
            if (cargoTierPrices.isEmpty()) {
                StitchCard {
                    Text(
                        text = "Aucun format colis disponible pour ce trajet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    cargoTierPrices.forEach { option ->
                        CargoTierCard(
                            option = option,
                            selected = option.tier == selectedTier,
                            onClick = { onTierSelected(option.tier) },
                            modifier = Modifier.width(132.dp)
                        )
                    }
                }
            }
        }

        StitchCard(modifier = Modifier.fillMaxWidth()) {
            ContactSection(
                title = "Expediteur",
                icon = Icons.Default.Unarchive,
                name = senderName,
                phone = senderPhone,
                onNameChange = onSenderNameChange,
                onPhoneChange = onSenderPhoneChange,
                enabled = !isLoading
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ContactSection(
                title = "Destinataire",
                icon = Icons.Default.Archive,
                name = receiverName,
                phone = receiverPhone,
                onNameChange = onReceiverNameChange,
                onPhoneChange = onReceiverPhoneChange,
                enabled = !isLoading
            )
        }

        StitchCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDetails = !showDetails },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Details supplementaires",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showDetails) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    minLines = 3,
                    maxLines = 5,
                    placeholder = { Text("Notes sur le colis, contenu ou consignes") },
                    shape = RoundedCornerShape(14.dp),
                    colors = stitchFieldColors()
                )
                Text(
                    text = "${description.length}/300",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StitchSectionLabel("Mode de paiement")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaymentChoiceCard(
                    title = "Prepaye",
                    icon = Icons.Default.CreditCard,
                    selected = paymentSource == "prepaid",
                    enabled = !isLoading,
                    onClick = { onPaymentSourceChange("prepaid") },
                    modifier = Modifier.weight(1f)
                )
                PaymentChoiceCard(
                    title = "A la livraison",
                    icon = Icons.Default.Payments,
                    selected = paymentSource == "pay_on_delivery",
                    enabled = !isLoading,
                    onClick = { onPaymentSourceChange("pay_on_delivery") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ContactSection(
    title: String,
    icon: ImageVector,
    name: String,
    phone: String,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            StitchSectionLabel(title)
        }
        TicketField(
            label = "Nom",
            value = name,
            onValueChange = onNameChange,
            placeholder = "Nom complet",
            enabled = enabled,
            leadingIcon = Icons.Default.Person
        )
        TicketField(
            label = "Telephone",
            value = phone,
            onValueChange = onPhoneChange,
            placeholder = "+213 ...",
            enabled = enabled,
            keyboardType = KeyboardType.Phone,
            leadingIcon = Icons.Default.Phone
        )
    }
}

@Composable
private fun CargoTierCard(
    option: CargoTierPriceUiModel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceContainerLowest
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = option.displayName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            StitchMonoText(
                text = option.amountLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primaryContainer
            )
        }
    }
}

@Composable
private fun CountActionButton(
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLowest
            )
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primaryContainer
        )
    }
}

@Composable
private fun PaymentChoiceCard(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                else MaterialTheme.colorScheme.surfaceContainerLowest
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TicketField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIcon: ImageVector? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StitchSectionLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            placeholder = { Text(placeholder) },
            leadingIcon = if (leadingIcon == null) null else {
                { Icon(imageVector = leadingIcon, contentDescription = null) }
            },
            shape = RoundedCornerShape(14.dp),
            colors = stitchFieldColors()
        )
    }
}

@Composable
private fun TicketFooterBar(
    totalLabel: String,
    buttonLabel: String,
    isLoading: Boolean,
    enabled: Boolean,
    onSubmit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                StitchSectionLabel("Total a payer")
                StitchMonoText(
                    text = totalLabel,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onSubmit,
                enabled = enabled && !isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ConfirmationNumber,
                            contentDescription = null
                        )
                        Text(
                            text = buttonLabel,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PassengerTicketPreviewCard(model: PassengerTicketPreviewModel) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 320.dp
            val horizontalPadding = if (compact) 8.dp else 12.dp
            val blockSpacing = if (compact) 8.dp else 10.dp
            val paperWidth = when {
                maxWidth < 290.dp -> maxWidth
                maxWidth < 360.dp -> maxWidth - 8.dp
                else -> 320.dp
            }
            val routeFont = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
            val titleFont = if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall
            val numberFont = if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall

            Box(
                modifier = Modifier
                    .width(paperWidth)
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(6.dp))
                    .padding(horizontal = horizontalPadding, vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(blockSpacing)) {
                Text(
                    text = "شركة آل سويقات لنقل المسافرين",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "النقال : ",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black,
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = "0660 82 69 83 / 0671 75 75 42 / 0671 75 75 41",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            maxLines = if (compact) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, Color.Black, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = if (compact) 5.dp else 6.dp)
                ) {
                    Text(
                        text = model.routeLabel,
                        style = routeFont.copy(fontWeight = FontWeight.Black),
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "تذكرة سفر",
                    style = titleFont.copy(fontWeight = FontWeight.Black),
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "ممنوع التدخين",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "№ ${ticketIdForPreview(model.ticketNumber, if (compact) 10 else 12)}",
                            style = numberFont.copy(
                                fontWeight = FontWeight.Normal,
                                fontFamily = FontFamily.Serif,
                            ),
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TicketPreviewDottedRow(
                    rightLabel = "الصعود",
                    rightValue = model.boardingPoint,
                    leftLabel = "النزول",
                    leftValue = model.destinationPoint,
                    compact = compact,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "السعر",
                            style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium).copy(fontWeight = FontWeight.Black),
                            color = Color.Black,
                        )
                        Box(
                            modifier = Modifier
                                .width(if (compact) 82.dp else 100.dp)
                                .border(1.5.dp, Color.Black)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = model.priceLabel.replace("DZD", "").trim(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                color = Color.Black,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(
                            text = "دج",
                            style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium).copy(fontWeight = FontWeight.Black),
                            color = Color.Black,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "التاريخ",
                            style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium).copy(fontWeight = FontWeight.Black),
                            color = Color.Black,
                        )
                        TicketPreviewDottedValue(
                            value = model.dateLabel,
                            modifier = Modifier.weight(1f),
                            compact = compact,
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFFCCCCCC))
                Text(
                    text = "إحتفظ بالتذكرة مدة السفر لتقديمها عند الطلب",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "المؤسسة غير مسؤولة عن الأمتعة الغير مسجلة",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            }
        }
    }
}

@Composable
private fun TicketPreviewDottedRow(
    rightLabel: String,
    rightValue: String,
    leftLabel: String,
    leftValue: String,
    compact: Boolean,
) {
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = rightLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                    color = Color.Black,
                )
                TicketPreviewDottedValue(value = rightValue, modifier = Modifier.weight(1f), compact = true)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = leftLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                    color = Color.Black,
                )
                TicketPreviewDottedValue(value = leftValue, modifier = Modifier.weight(1f), compact = true)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = rightLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                color = Color.Black,
            )
            TicketPreviewDottedValue(value = rightValue, modifier = Modifier.weight(1f), compact = false)
            Text(
                text = leftLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                color = Color.Black,
            )
            TicketPreviewDottedValue(value = leftValue, modifier = Modifier.weight(1f), compact = false)
        }
    }
}

@Composable
private fun TicketPreviewDottedValue(
    value: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Box(
        modifier = modifier
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(
                    color = Color.Black,
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Butt,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                )
            }
            .padding(bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value,
            style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium).copy(fontWeight = FontWeight.Bold),
            color = Color.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun stitchFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    focusedBorderColor = MaterialTheme.colorScheme.primaryContainer,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
)

private fun formatTicketTotal(amount: Long, currency: String): String {
    return "${formatCompact(amount)}.00 $currency"
}

private fun forwardDestinationOptions(stops: List<String>, boardingPoint: String): List<String> {
    val normalized = normalizeStops(stops)
    val boardingIndex = normalized.indexOfFirst { it.equals(boardingPoint.trim(), ignoreCase = true) }
    if (boardingIndex == -1) {
        return emptyList()
    }
    return normalized.drop(boardingIndex + 1)
}

private fun isForwardPathSelected(stops: List<String>, boardingPoint: String, alightingPoint: String): Boolean {
    val normalized = normalizeStops(stops)
    val boardingIndex = normalized.indexOfFirst { it.equals(boardingPoint.trim(), ignoreCase = true) }
    val alightingIndex = normalized.indexOfFirst { it.equals(alightingPoint.trim(), ignoreCase = true) }
    return boardingIndex >= 0 && alightingIndex > boardingIndex
}

private fun computeForwardFare(
    routeStops: List<String>,
    routeSegments: List<com.souigat.mobile.domain.model.TripRouteSegmentTariff>,
    boardingPoint: String,
    alightingPoint: String,
): Long? {
    val normalizedStops = normalizeStops(routeStops)
    val boardingIndex = normalizedStops.indexOfFirst { it.equals(boardingPoint.trim(), ignoreCase = true) }
    val alightingIndex = normalizedStops.indexOfFirst { it.equals(alightingPoint.trim(), ignoreCase = true) }
    if (boardingIndex == -1 || alightingIndex <= boardingIndex) {
        return null
    }

    var total = 0L
    for (index in boardingIndex until alightingIndex) {
        val fromOrder = index + 1
        val toOrder = index + 2
        val segment = routeSegments.firstOrNull {
            it.fromStopOrder == fromOrder && it.toStopOrder == toOrder
        } ?: return null
        total += segment.passengerPrice
    }
    return total
}

private fun normalizeStops(stops: List<String>): List<String> {
    return stops.map { it.trim() }.filter { it.isNotBlank() }.distinct()
}

private fun boardingStopOptions(stops: List<String>): List<String> {
    val normalized = normalizeStops(stops)
    return if (normalized.size > 1) normalized.dropLast(1) else normalized
}

private fun compactTicketNumber(value: String, maxChars: Int): String {
    val trimmed = value.trim()
    if (trimmed.length <= maxChars) return trimmed
    return trimmed.take(maxChars)
}

private fun ticketIdForPreview(ticketNumber: String, maxChars: Int): String {
    val compactId = ticketNumber.substringAfterLast('-').trim()
    if (compactId.isNotBlank()) {
        return compactTicketNumber(compactId, maxChars)
    }
    return compactTicketNumber(ticketNumber, maxChars)
}
