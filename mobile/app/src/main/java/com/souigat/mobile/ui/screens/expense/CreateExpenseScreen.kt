package com.souigat.mobile.ui.screens.expense

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
fun CreateExpenseScreen(
    viewModel: CreateExpenseViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        if (uiState is CreateExpenseUiState.Success) {
            val msg = (uiState as CreateExpenseUiState.Success).message
            snackbarHostState.showSnackbar(msg)
            viewModel.resetState()
            onNavigateBack()
        } else if (uiState is CreateExpenseUiState.Error) {
            val err = (uiState as CreateExpenseUiState.Error).message
            snackbarHostState.showSnackbar(err)
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nouvelle Dépense") },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isLoading = uiState is CreateExpenseUiState.Loading

            ExpenseForm(viewModel, isLoading)

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseForm(viewModel: CreateExpenseViewModel, isLoading: Boolean) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("food") } // fuel | food | maintenance | tolls | other
    var expandedCategory by remember { mutableStateOf(false) }

    val categories = mapOf(
        "fuel" to "Carburant",
        "food" to "Nourriture",
        "maintenance" to "Entretien",
        "tolls" to "Péages",
        "other" to "Autre"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Montant (${viewModel.currency})*") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !isLoading
        )

        ExposedDropdownMenuBox(
            expanded = expandedCategory,
            onExpandedChange = { expandedCategory = !expandedCategory }
        ) {
            OutlinedTextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = categories[category] ?: "Nourriture",
                onValueChange = { },
                label = { Text("Catégorie*") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                enabled = !isLoading
            )
            ExposedDropdownMenu(
                expanded = expandedCategory,
                onDismissRequest = { expandedCategory = false }
            ) {
                categories.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { 
                            category = key
                            expandedCategory = false 
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (Optionnel)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.createExpense(amount, category, description) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = amount.isNotBlank() && !isLoading
        ) {
            Text("Enregistrer", fontWeight = FontWeight.Bold)
        }
    }
}
