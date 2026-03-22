package com.souigat.mobile.ui.screens.tickets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.ui.components.EmptyStatePanel
import com.souigat.mobile.ui.model.CargoTierPriceUiModel
import com.souigat.mobile.util.formatCompact
import com.souigat.mobile.util.formatCurrency
import com.souigat.mobile.util.parseCurrencyInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTicketScreen(
    viewModel: CreateTicketViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var passengerCount by rememberSaveable { mutableStateOf(1) }
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

    LaunchedEffect(formState) {
        val ready = formState as? TicketFormHeaderState.Ready ?: return@LaunchedEffect
        if (passengerPriceInput.isBlank()) {
            passengerPriceInput = formatCompact(ready.passengerBasePriceCentimes)
        }
        if (cargoTier.isBlank() || ready.cargoTierPrices.none { it.tier == cargoTier }) {
            cargoTier = ready.cargoTierPrices.firstOrNull()?.tier.orEmpty()
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is CreateTicketUiState.Success -> {
                viewModel.resetState()
                onNavigateBack()
            }
            is CreateTicketUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    val isLoading = uiState is CreateTicketUiState.Loading
    val readyState = formState as? TicketFormHeaderState.Ready
    val passengerPriceCentimes = parseCurrencyInput(passengerPriceInput) ?: 0L
    val passengerTotal = passengerPriceCentimes * passengerCount
    val cargoPrice = readyState?.cargoTierPrices?.firstOrNull { it.tier == cargoTier }?.valueCentimes ?: 0L
    val footerAmount = if (selectedTab == 0) passengerTotal else cargoPrice
    val footerCurrency = readyState?.header?.currency ?: "DZD"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nouveau billet", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::retryLookup) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                ),
                modifier = Modifier.border(1.dp, Color(0xFFE2E5EA))
            )
        },
        bottomBar = {
            if (readyState != null) {
                TicketFooterBar(
                    totalLabel = formatCurrency(footerAmount, footerCurrency),
                    buttonLabel = if (selectedTab == 0) {
                        if (passengerCount > 1) "Créer $passengerCount billets" else "Créer billet"
                    } else {
                        "Enregistrer colis"
                    },
                    isLoading = isLoading,
                    enabled = if (selectedTab == 0) {
                        passengerPriceCentimes > 0 && passengerCount in 1..50
                    } else {
                        senderName.isNotBlank() && receiverName.isNotBlank() && cargoPrice > 0
                    },
                    onSubmit = {
                        if (selectedTab == 0) {
                            viewModel.createPassengerTicketBatch(
                                count = passengerCount,
                                manualPriceInput = passengerPriceInput,
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
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = formState) {
            TicketFormHeaderState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is TicketFormHeaderState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp), contentAlignment = Alignment.Center) {
                    EmptyStatePanel(
                        icon = Icons.Default.Inventory2,
                        title = "Trajet introuvable",
                        message = state.message,
                        primaryActionLabel = "Réessayer",
                        onPrimaryAction = viewModel::retryLookup,
                        secondaryActionLabel = "Retour",
                        onSecondaryAction = onNavigateBack
                    )
                }
            }
            is TicketFormHeaderState.Ready -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 128.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item(key = "header") {
                        TripSummaryHeader(
                            origin = state.header.origin,
                            destination = state.header.destination,
                            busPlate = state.header.busPlate,
                            statusLabel = state.header.statusLabel
                        )
                    }

                    item(key = "tabs") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedTab == 0) Color.White else Color.Transparent)
                                    .clickable { selectedTab = 0 }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Passager",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedTab == 1) Color.White else Color.Transparent)
                                    .clickable { selectedTab = 1 }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Colis",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    item(key = "content") {
                        if (selectedTab == 0) {
                            PassengerTicketForm(
                                routeOrigin = state.header.origin,
                                routeDestination = state.header.destination,
                                passengerPriceInput = passengerPriceInput,
                                onPassengerPriceChange = {
                                    passengerPriceInput = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' || ch == ' ' }
                                },
                                suggestedPriceLabel = formatCurrency(state.passengerBasePriceCentimes, state.header.currency),
                                passengerCount = passengerCount,
                                onIncreaseCount = { if (passengerCount < 50) passengerCount += 1 },
                                onDecreaseCount = { if (passengerCount > 1) passengerCount -= 1 },
                                seatNumber = seatNumber,
                                onSeatNumberChange = { seatNumber = it },
                                boardingPoint = boardingPoint,
                                onBoardingPointChange = { boardingPoint = it },
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
                                onSenderPhoneChange = { senderPhone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' } },
                                receiverName = receiverName,
                                onReceiverNameChange = { receiverName = it },
                                receiverPhone = receiverPhone,
                                onReceiverPhoneChange = { receiverPhone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' } },
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
private fun TripSummaryHeader(origin: String, destination: String, busPlate: String, statusLabel: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "LIGNE ACTUELLE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = statusLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(text = origin, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(text = destination, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "BUS NO",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = busPlate,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PassengerTicketForm(
    routeOrigin: String,
    routeDestination: String,
    passengerPriceInput: String,
    onPassengerPriceChange: (String) -> Unit,
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
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // Places
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "NOMBRE DE PLACES",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDecreaseCount,
                    enabled = !isLoading && passengerCount > 1,
                    modifier = Modifier.size(56.dp).background(Color.White, RoundedCornerShape(8.dp)).shadow(1.dp, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Descendre", tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = String.format("%02d", passengerCount),
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onIncreaseCount,
                    enabled = !isLoading && passengerCount < 50,
                    modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)).shadow(2.dp, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Augmenter", tint = Color.White)
                }
            }
        }

        // Prix Unitaire
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "PRIX UNITAIRE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Tarif conseille: $suggestedPriceLabel",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            OutlinedTextField(
                value = passengerPriceInput,
                onValueChange = onPassengerPriceChange,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                trailingIcon = {
                    Text("DZD", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(end = 16.dp))
                }
            )
        }

        // Stops (Boarding / Alighting placeholder for Compose native)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "ARRÊT DE DESTINATION", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            
            // Simulating the horizontal pills from Stitch
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val stops = listOf("Oran (Terminus)", "Chlef", "Relizane", "Mostaganem")
                stops.forEachIndexed { index, stop ->
                    Box(
                        modifier = Modifier
                            .background(if (index == 0) MaterialTheme.colorScheme.primaryContainer else Color.White, RoundedCornerShape(50))
                            .border(1.dp, if (index == 0) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f), RoundedCornerShape(50))
                            .clickable { onAlightingPointChange(stop) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stop,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (index==0) FontWeight.Bold else FontWeight.Medium),
                            color = if (index == 0) Color.White else MaterialTheme.colorScheme.onSurface 
                        )
                    }
                }
            }
        }

        FormTextField("Numéro de siège", seatNumber, onSeatNumberChange, "Optionnel", !isLoading, KeyboardType.Number)

        // Payment Method
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "MODE DE PAIEMENT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceCard(
                    title = "Espèces",
                    icon = Icons.Default.Payments,
                    selected = paymentSource == "cash",
                    enabled = !isLoading,
                    onClick = { onPaymentSourceChange("cash") },
                    modifier = Modifier.weight(1f)
                )
                ChoiceCard(
                    title = "Prépayé",
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
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "TAILLE DU COLIS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            cargoTierPrices.forEach { option ->
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTierSelected(option.tier) }
                    .background(if (option.tier == selectedTier) MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.05f) else Color.White, RoundedCornerShape(12.dp))
                    .border(if (option.tier == selectedTier) 2.dp else 1.dp, if (option.tier == selectedTier) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
                ) {
                    Column {
                        Text(text = option.label.substringBefore(" - "), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        Text(text = option.label.substringAfter(" - ", option.label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }

        FormTextField("Nom expéditeur", senderName, onSenderNameChange, "Obligatoire", !isLoading)
        FormTextField("Téléphone expéditeur", senderPhone, onSenderPhoneChange, "Optionnel", !isLoading, KeyboardType.Phone)
        FormTextField("Nom destinataire", receiverName, onReceiverNameChange, "Obligatoire", !isLoading)
        FormTextField("Téléphone destinataire", receiverPhone, onReceiverPhoneChange, "Optionnel", !isLoading, KeyboardType.Phone)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "DESCRIPTION", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                modifier = Modifier.fillMaxWidth().height(120.dp),
                enabled = !isLoading,
                maxLines = 4,
                placeholder = { Text("Notes de colis, contenu, consignes") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            )
            Text(text = "${description.length}/300", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        }
        
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "MODE DE PAIEMENT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceCard(
                    title = "Prépayé",
                    icon = Icons.Default.CreditCard,
                    selected = paymentSource == "prepaid",
                    enabled = !isLoading,
                    onClick = { onPaymentSourceChange("prepaid") },
                    modifier = Modifier.weight(1f)
                )
                ChoiceCard(
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
private fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha=0.05f) else Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().align(Alignment.Center)) {
            Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer)
        }
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
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f))
            .padding(16.dp)
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TOTAL À PAYER",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = totalLabel,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Button(
                onClick = onSubmit,
                enabled = enabled && !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.height(56.dp).weight(1f).padding(start = 16.dp).shadow(4.dp, RoundedCornerShape(12.dp))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text(buttonLabel, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
