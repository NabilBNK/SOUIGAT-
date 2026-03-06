package com.souigat.mobile.ui.screens.tickets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTicketScreen(
    viewModel: CreateTicketViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Passager", "Colis")
    
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        if (uiState is CreateTicketUiState.Success) {
            val msg = (uiState as CreateTicketUiState.Success).message
            snackbarHostState.showSnackbar(msg)
            viewModel.resetState()
            onNavigateBack()
        } else if (uiState is CreateTicketUiState.Error) {
            val err = (uiState as CreateTicketUiState.Error).message
            snackbarHostState.showSnackbar(err)
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nouveau Billet") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            
            val isLoading = uiState is CreateTicketUiState.Loading

            Box(modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)) {
                if (selectedTabIndex == 0) {
                    PassengerForm(viewModel, isLoading)
                } else {
                    CargoForm(viewModel, isLoading)
                }
                
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerForm(viewModel: CreateTicketViewModel, isLoading: Boolean) {
    var passengerName by remember { mutableStateOf("") }
    var seatNumber by remember { mutableStateOf("") }
    var paymentSource by remember { mutableStateOf("cash") } // cash or prepaid
    var expandedPayment by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = passengerName,
            onValueChange = { passengerName = it },
            label = { Text("Nom du Passager*") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isLoading
        )

        OutlinedTextField(
            value = seatNumber,
            onValueChange = { seatNumber = it },
            label = { Text("Numéro de siège (Optionnel)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isLoading
        )

        ExposedDropdownMenuBox(
            expanded = expandedPayment,
            onExpandedChange = { expandedPayment = !expandedPayment }
        ) {
            OutlinedTextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = if (paymentSource == "cash") "Espèces" else "Prépayé",
                onValueChange = { },
                label = { Text("Paiement*") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPayment) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expandedPayment,
                onDismissRequest = { expandedPayment = false }
            ) {
                DropdownMenuItem(text = { Text("Espèces") }, onClick = { paymentSource = "cash"; expandedPayment = false })
                DropdownMenuItem(text = { Text("Prépayé") }, onClick = { paymentSource = "prepaid"; expandedPayment = false })
            }
        }
        
        Text(
            text = "Prix: ${viewModel.passPriceStr} ${viewModel.currency}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.createPassengerTicket(passengerName, paymentSource, seatNumber) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = passengerName.isNotBlank() && !isLoading
        ) {
            Text("Créer le billet", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CargoForm(viewModel: CreateTicketViewModel, isLoading: Boolean) {
    var senderName by remember { mutableStateOf("") }
    var senderPhone by remember { mutableStateOf("") }
    var receiverName by remember { mutableStateOf("") }
    var receiverPhone by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    
    var cargoTier by remember { mutableStateOf("small") } 
    var expandedTier by remember { mutableStateOf(false) }

    var paymentSource by remember { mutableStateOf("prepaid") } // prepaid or pay_on_delivery
    var expandedPayment by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = senderName,
            onValueChange = { senderName = it },
            label = { Text("Nom de l'expéditeur*") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isLoading
        )
        OutlinedTextField(
            value = senderPhone,
            onValueChange = { senderPhone = it },
            label = { Text("Téléphone de l'expéditeur") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            enabled = !isLoading
        )
        
        OutlinedTextField(
            value = receiverName,
            onValueChange = { receiverName = it },
            label = { Text("Nom du destinataire*") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isLoading
        )
        OutlinedTextField(
            value = receiverPhone,
            onValueChange = { receiverPhone = it },
            label = { Text("Téléphone du destinataire") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            enabled = !isLoading
        )

        ExposedDropdownMenuBox(
            expanded = expandedTier,
            onExpandedChange = { expandedTier = !expandedTier }
        ) {
            OutlinedTextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = when (cargoTier) {
                    "small" -> "Petit colis"
                    "medium" -> "Colis moyen"
                    "large" -> "Grand colis"
                    else -> ""
                },
                onValueChange = { },
                label = { Text("Taille du colis*") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTier) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expandedTier,
                onDismissRequest = { expandedTier = false }
            ) {
                DropdownMenuItem(text = { Text("Petit colis (${viewModel.smallPriceStr} ${viewModel.currency})") }, onClick = { cargoTier = "small"; expandedTier = false })
                DropdownMenuItem(text = { Text("Colis moyen (${viewModel.medPriceStr} ${viewModel.currency})") }, onClick = { cargoTier = "medium"; expandedTier = false })
                DropdownMenuItem(text = { Text("Grand colis (${viewModel.largePriceStr} ${viewModel.currency})") }, onClick = { cargoTier = "large"; expandedTier = false })
            }
        }

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (Optionnelle)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        ExposedDropdownMenuBox(
            expanded = expandedPayment,
            onExpandedChange = { expandedPayment = !expandedPayment }
        ) {
            OutlinedTextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = if (paymentSource == "prepaid") "Prépayé" else "Paiement à la livraison",
                onValueChange = { },
                label = { Text("Paiement*") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPayment) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expandedPayment,
                onDismissRequest = { expandedPayment = false }
            ) {
                DropdownMenuItem(text = { Text("Prépayé") }, onClick = { paymentSource = "prepaid"; expandedPayment = false })
                DropdownMenuItem(text = { Text("Paiement à la livraison") }, onClick = { paymentSource = "pay_on_delivery"; expandedPayment = false })
            }
        }

        val activePrice = when (cargoTier) {
            "small" -> viewModel.smallPriceStr
            "medium" -> viewModel.medPriceStr
            "large" -> viewModel.largePriceStr
            else -> "0"
        }
        Text(
            text = "Prix affiché: $activePrice ${viewModel.currency}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.createCargoTicket(senderName, senderPhone, receiverName, receiverPhone, cargoTier, description, paymentSource) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = senderName.isNotBlank() && receiverName.isNotBlank() && !isLoading
        ) {
            Text("Créer le colis", fontWeight = FontWeight.Bold)
        }
    }
}
