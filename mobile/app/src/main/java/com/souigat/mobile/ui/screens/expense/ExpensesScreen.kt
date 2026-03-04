package com.souigat.mobile.ui.screens.expense

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** TODO Phase 3.4: Expense list and create expense form. */
@Composable
fun ExpensesScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Expenses — Phase 3.4", style = MaterialTheme.typography.titleMedium)
    }
}
