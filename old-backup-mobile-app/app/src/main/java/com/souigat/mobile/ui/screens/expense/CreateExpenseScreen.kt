package com.souigat.mobile.ui.screens.expense

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.ui.components.EmptyStatePanel
import com.souigat.mobile.ui.components.TripSummaryCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateExpenseScreen(
    viewModel: CreateExpenseViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var amount by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("fuel") }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is CreateExpenseUiState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetState()
                onNavigateBack()
            }

            is CreateExpenseUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetState()
            }

            else -> Unit
        }
    }

    val isLoading = uiState is CreateExpenseUiState.Loading
    val readyState = formState as? ExpenseFormHeaderState.Ready

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nouvelle depense") },
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
                ExpenseFooterBar(
                    buttonLabel = "Enregistrer",
                    isLoading = isLoading,
                    enabled = amount.isNotBlank() && !isLoading,
                    onSubmit = { viewModel.createExpense(amount, category, description) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = formState) {
            ExpenseFormHeaderState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is ExpenseFormHeaderState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStatePanel(
                        icon = Icons.Default.Receipt,
                        title = "Trajet introuvable",
                        message = state.message,
                        primaryActionLabel = "Reessayer",
                        onPrimaryAction = viewModel::retryLookup,
                        secondaryActionLabel = "Retour",
                        onSecondaryAction = onNavigateBack
                    )
                }
            }

            is ExpenseFormHeaderState.Ready -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 120.dp),
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

                    item(key = "amount") {
                        AmountHeroCard(
                            amount = amount,
                            currency = state.header.currency,
                            onAmountChange = {
                                amount = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' || ch == ' ' }
                            },
                            enabled = !isLoading
                        )
                    }

                    item(key = "categories_title") {
                        Text(
                            text = "Categorie",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    item(key = "categories") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExpenseCategoryRow(
                                left = ExpenseCategoryOption("fuel", "Carburant", Icons.Default.LocalGasStation),
                                right = ExpenseCategoryOption("maintenance", "Maintenance", Icons.Default.Build),
                                selectedCategory = category,
                                onCategorySelected = { category = it }
                            )
                            ExpenseCategoryRow(
                                left = ExpenseCategoryOption("food", "Repas", Icons.Default.Restaurant),
                                right = ExpenseCategoryOption("tolls", "Peages", Icons.Default.Toll),
                                selectedCategory = category,
                                onCategorySelected = { category = it }
                            )
                            ExpenseCategoryRow(
                                left = ExpenseCategoryOption("other", "Autre", Icons.Default.MoreHoriz),
                                right = null,
                                selectedCategory = category,
                                onCategorySelected = { category = it }
                            )
                        }
                    }

                    item(key = "description") {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { if (it.length <= 200) description = it },
                            label = { Text("Description") },
                            placeholder = { Text("Notes, detail du paiement, reference") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            enabled = !isLoading,
                            maxLines = 5
                        )
                    }

                    item(key = "description_count") {
                        Text(
                            text = "${description.length}/200",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private data class ExpenseCategoryOption(
    val id: String,
    val label: String,
    val icon: ImageVector
)

@Composable
private fun AmountHeroCard(
    amount: String,
    currency: String,
    onAmountChange: (String) -> Unit,
    enabled: Boolean
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Montant total",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                label = { Text("Montant ($currency)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun ExpenseCategoryRow(
    left: ExpenseCategoryOption,
    right: ExpenseCategoryOption?,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExpenseCategoryCard(
            option = left,
            selected = selectedCategory == left.id,
            onClick = { onCategorySelected(left.id) },
            modifier = Modifier.weight(1f)
        )
        if (right != null) {
            ExpenseCategoryCard(
                option = right,
                selected = selectedCategory == right.id,
                onClick = { onCategorySelected(right.id) },
                modifier = Modifier.weight(1f)
            )
        } else {
            Box(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ExpenseCategoryCard(
    option: ExpenseCategoryOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ExpenseFooterBar(
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
        Button(
            onClick = onSubmit,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
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
