package com.souigat.mobile.ui.screens.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.souigat.mobile.ui.components.ConductorPanelSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementSummaryScreen(
    preview: SettlementPreviewUiModel,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remise de caisse") },
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
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ConductorPanelSurface(shape = MaterialTheme.shapes.extraLarge) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Remettez ${preview.netCashExpectedLabel} au bureau ${preview.officeName}.",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Le conducteur ne modifie pas ce reglement. Cette vue est un recapitulatif apres cloture.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SettlementSummaryCard(
                    title = "Caisse a remettre",
                    icon = Icons.Default.LocalAtm,
                    lines = listOf(
                        "Especes collectees" to preview.expectedTotalCashLabel,
                        "Net attendu" to preview.netCashExpectedLabel,
                    )
                )
            }

            item {
                SettlementSummaryCard(
                    title = "Remboursements",
                    icon = Icons.Default.AccountBalance,
                    lines = listOf(
                        "Depenses a rembourser" to preview.expensesToReimburseLabel,
                        "Prevente agence" to preview.agencyPresaleLabel,
                    )
                )
            }

            item {
                SettlementSummaryCard(
                    title = "Encaissements restants",
                    icon = Icons.Default.ReceiptLong,
                    lines = listOf(
                        "Cargo a percevoir (POD)" to preview.outstandingCargoDeliveryLabel,
                        "Statut" to preview.status,
                    )
                )
            }

            item {
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retour au trajet")
                }
            }
        }
    }
}

@Composable
private fun SettlementSummaryCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    lines: List<Pair<String, String>>
) {
    ConductorPanelSurface(shape = MaterialTheme.shapes.extraLarge) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            lines.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
