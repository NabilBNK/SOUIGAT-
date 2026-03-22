package com.souigat.mobile.ui.screens.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Save
import           androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souigat.mobile.ui.components.EmptyStatePanel

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nouvelle dépense", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                ),
                modifier = Modifier.border(1.dp, Color(0xFFE2E5EA))
            )
        },
        bottomBar = {
            if (formState is ExpenseFormHeaderState.Ready) {
                ExpenseFooterBar(
                    buttonLabel = "Enregistrer la dépense",
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
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ExpenseFormHeaderState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp), contentAlignment = Alignment.Center) {
                    EmptyStatePanel(
                        icon = Icons.Default.Receipt,
                        title = "Trajet introuvable",
                        message = state.message,
                        primaryActionLabel = "Réessayer",
                        onPrimaryAction = viewModel::retryLookup,
                        secondaryActionLabel = "Retour",
                        onSecondaryAction = onNavigateBack
                    )
                }
            }
            is ExpenseFormHeaderState.Ready -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Hero Amount
                    item(key = "amount") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "MONTANT DE LA DÉPENSE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                                TextField(
                                    value = amount,
                                    onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' || ch == ' ' } },
                                    enabled = !isLoading,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                    placeholder = { Text("0.00", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier.width(160.dp) // Auto expand logically
                                )
                                Text(
                                    text = state.header.currency,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                )
                            }
                            Box(modifier = Modifier.width(96.dp).height(4.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.2f), RoundedCornerShape(50)))
                        }
                    }

                    // Categories Grid
                    item(key = "categories") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "CATÉGORIE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            val categories = listOf(
                                ExpenseCategoryOption("fuel", "Carburant", Icons.Default.LocalGasStation),
                                ExpenseCategoryOption("food", "Restaurant", Icons.Default.Restaurant),
                                ExpenseCategoryOption("maintenance", "Entretien", Icons.Default.Build),
                                ExpenseCategoryOption("tolls", "Péage", Icons.Default.Toll),
                                ExpenseCategoryOption("other", "Autres", Icons.Default.MoreHoriz)
                            )
                            
                            // Emulating a 3-column grid
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ExpenseCategoryCard(option = categories[0], selected = category == "fuel", onClick = { category = "fuel" }, modifier = Modifier.weight(1f))
                                ExpenseCategoryCard(option = categories[1], selected = category == "food", onClick = { category = "food" }, modifier = Modifier.weight(1f))
                                ExpenseCategoryCard(option = categories[2], selected = category == "maintenance", onClick = { category = "maintenance" }, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ExpenseCategoryCard(option = categories[3], selected = category == "tolls", onClick = { category = "tolls" }, modifier = Modifier.weight(1f))
                                ExpenseCategoryCard(option = categories[4], selected = category == "other", onClick = { category = "other" }, modifier = Modifier.weight(1f))
                                Box(modifier = Modifier.weight(1f)) // empty slot
                            }
                        }
                    }

                    // Description
                    item(key = "description") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "DESCRIPTION",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "${description.length}/200",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = description,
                                onValueChange = { if (it.length <= 200) description = it },
                                placeholder = { Text("Ajouter des détails sur la dépense...") },
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                enabled = !isLoading,
                                maxLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primaryContainer,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            )
                        }
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
private fun ExpenseCategoryCard(
    option: ExpenseCategoryOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f) // Square card
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.White)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f), RoundedCornerShape(12.dp))
            .shadow(if (selected) 4.dp else 1.dp, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.9f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.1f))
            .padding(16.dp)
            .padding(bottom = 8.dp)
    ) {
        Button(
            onClick = onSubmit,
            enabled = enabled && !isLoading,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().height(56.dp).shadow(8.dp, RoundedCornerShape(50))
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(buttonLabel.uppercase(), fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.5.sp)
                }
            }
        }
    }
}
