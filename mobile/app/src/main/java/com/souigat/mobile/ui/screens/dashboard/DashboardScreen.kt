package com.souigat.mobile.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** TODO Phase 3.2: Trip dashboard with stats, current route card, activity list. */
@Composable
fun DashboardScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Dashboard — Phase 3.2", style = MaterialTheme.typography.titleMedium)
    }
}
