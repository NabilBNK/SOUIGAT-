package com.souigat.mobile.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** TODO Phase 3.1: Profile with sync status and MIUI battery whitelist guide. */
@Composable
fun ProfileScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Profile — Phase 3.1", style = MaterialTheme.typography.titleMedium)
    }
}
