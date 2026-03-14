package com.souigat.mobile.ui.screens.tickets
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.ui.components.EmptyStatePanel
import com.souigat.mobile.ui.components.TripSummaryCard
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
                snackbarHostState.showSnackbar(state.message)
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
                title = { Text("Nouveau billet") },
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
            if (readyState != null) {
                TicketFooterBar(
                    totalLabel = formatCurrency(footerAmount, footerCurrency),
                    buttonLabel = if (selectedTab == 0) {
                        if (passengerCount > 1) "Creer $passengerCount billets" else "Creer le billet"
                    } else {
                        "Enregistrer le colis"
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
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
                    EmptyStatePanel(
                        icon = Icons.Default.Inventory2,
                        title = "Trajet introuvable",
                        message = state.message,
                        primaryActionLabel = "Reessayer",
                        onPrimaryAction = viewModel::retryLookup,
                        secondaryActionLabel = "Retour",
                        onSecondaryAction = onNavigateBack
                    )
                }
            }

            is TicketFormHeaderState.Ready -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 128.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "header") {
                        TripSummaryCard(
                            origin = state.header.origin,
                            destination = state.header.destination,
                            busPlate = state.header.busPlate,
                            departureLabel = state.header.departureLabel,
                            statusLabel = state.header.statusLabel,
                            supportingLabel = state.header.currency
                        )
                    }

                    item(key = "tabs") {
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Passager") },
                                icon = { Icon(Icons.Default.Person, contentDescription = null) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Colis") },
                                icon = { Icon(Icons.Default.Inventory2, contentDescription = null) }
                            )
                        }
                    }

                    item(key = "content") {
                        if (selectedTab == 0) {
                            PassengerTicketForm(
                                routeOrigin = state.header.origin,
                                routeDestination = state.header.destination,
                                passengerPriceInput = passengerPriceInput,
                                onPassengerPriceChange = {
                                    passengerPriceInput = it.filter { ch ->
                                        ch.isDigit() || ch == ',' || ch == '.' || ch == ' '
                                    }
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        QuantityCard(
            title = "Nombre de passagers",
            value = passengerCount,
            onIncrease = onIncreaseCount,
            onDecrease = onDecreaseCount,
            enabled = !isLoading
        )

        OutlinedTextField(
            value = passengerPriceInput,
            onValueChange = onPassengerPriceChange,
            label = { Text("Prix manuel (DZD)") },
            placeholder = { Text("Ex: 1 200") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        Text(
            text = "Tarif trajet complet conseille: $suggestedPriceLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FormTextField(
            value = seatNumber,
            onValueChange = onSeatNumberChange,
            label = "Numero de siege",
            placeholder = "Optionnel",
            enabled = !isLoading,
            keyboardType = KeyboardType.Number
        )
        FormTextField(
            value = boardingPoint,
            onValueChange = onBoardingPointChange,
            label = "Point de montee optionnel",
            placeholder = "Optionnel",
            enabled = !isLoading
        )
        OptionalStopSelection(
            title = "Selection rapide du point de montee",
            currentValue = boardingPoint,
            routeOrigin = routeOrigin,
            routeDestination = routeDestination,
            onSelect = onBoardingPointChange,
            enabled = !isLoading
        )
        FormTextField(
            value = alightingPoint,
            onValueChange = onAlightingPointChange,
            label = "Point de descente optionnel",
            placeholder = "Optionnel",
            enabled = !isLoading
        )
        OptionalStopSelection(
            title = "Selection rapide du point de descente",
            currentValue = alightingPoint,
            routeOrigin = routeOrigin,
            routeDestination = routeDestination,
            onSelect = onAlightingPointChange,
            enabled = !isLoading
        )

        SelectionSection(title = "Paiement") {
            ChoiceCard(
                title = "Especes",
                subtitle = "Encaissement immediat",
                selected = paymentSource == "cash",
                enabled = !isLoading,
                onClick = { onPaymentSourceChange("cash") }
            )
            ChoiceCard(
                title = "Prepaye",
                subtitle = "Billet deja regle",
                selected = paymentSource == "prepaid",
                enabled = !isLoading,
                onClick = { onPaymentSourceChange("prepaid") }
            )
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SelectionSection(title = "Taille du colis") {
            cargoTierPrices.forEach { option ->
                ChoiceCard(
                    title = option.label.substringBefore(" - "),
                    subtitle = option.label.substringAfter(" - ", option.label),
                    selected = option.tier == selectedTier,
                    enabled = !isLoading,
                    onClick = { onTierSelected(option.tier) }
                )
            }
        }

        FormTextField(
            value = senderName,
            onValueChange = onSenderNameChange,
            label = "Nom expediteur",
            placeholder = "Obligatoire",
            enabled = !isLoading
        )
        FormTextField(
            value = senderPhone,
            onValueChange = onSenderPhoneChange,
            label = "Telephone expediteur",
            placeholder = "Optionnel",
            enabled = !isLoading,
            keyboardType = KeyboardType.Phone
        )
        FormTextField(
            value = receiverName,
            onValueChange = onReceiverNameChange,
            label = "Nom destinataire",
            placeholder = "Obligatoire",
            enabled = !isLoading
        )
        FormTextField(
            value = receiverPhone,
            onValueChange = onReceiverPhoneChange,
            label = "Telephone destinataire",
            placeholder = "Optionnel",
            enabled = !isLoading,
            keyboardType = KeyboardType.Phone
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            placeholder = { Text("Notes de colis, contenu, consignes") },
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            enabled = !isLoading,
            maxLines = 5
        )
        Text(
            text = "${description.length}/300",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SelectionSection(title = "Paiement") {
            ChoiceCard(
                title = "Prepaye",
                subtitle = "Regle avant chargement",
                selected = paymentSource == "prepaid",
                enabled = !isLoading,
                onClick = { onPaymentSourceChange("prepaid") }
            )
            ChoiceCard(
                title = "Paiement livraison",
                subtitle = "Encaisse a la reception",
                selected = paymentSource == "pay_on_delivery",
                enabled = !isLoading,
                onClick = { onPaymentSourceChange("pay_on_delivery") }
            )
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
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = totalLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = onSubmit,
                enabled = enabled && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(buttonLabel)
                }
            }
        }
    }
}

@Composable
private fun QuantityCard(
    title: String,
    value: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    enabled: Boolean
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Ajustez rapidement le lot hors ligne",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                StepperButton(label = "-", enabled = enabled && value > 1, onClick = onDecrease)
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                StepperButton(label = "+", enabled = enabled && value < 50, onClick = onIncrease)
            }
        }
    }
}

@Composable
private fun StepperButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OptionalStopSelection(
    title: String,
    currentValue: String,
    routeOrigin: String,
    routeDestination: String,
    onSelect: (String) -> Unit,
    enabled: Boolean
) {
    SelectionSection(title = title) {
        ChoiceCard(
            title = "Non precise",
            subtitle = "Laisser le conducteur saisir ou laisser vide",
            selected = currentValue.isBlank(),
            enabled = enabled,
            onClick = { onSelect("") }
        )
        ChoiceCard(
            title = routeOrigin,
            subtitle = "Utiliser l'origine du trajet",
            selected = currentValue.equals(routeOrigin, ignoreCase = true),
            enabled = enabled,
            onClick = { onSelect(routeOrigin) }
        )
        ChoiceCard(
            title = routeDestination,
            subtitle = "Utiliser la destination du trajet",
            selected = currentValue.equals(routeDestination, ignoreCase = true),
            enabled = enabled,
            onClick = { onSelect(routeDestination) }
        )
    }
}

@Composable
private fun SelectionSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true
    )
}
