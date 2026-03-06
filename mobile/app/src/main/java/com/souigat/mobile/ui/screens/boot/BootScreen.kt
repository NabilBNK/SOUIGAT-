package com.souigat.mobile.ui.screens.boot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun BootScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRole: (String) -> Unit,
    viewModel: BootViewModel = hiltViewModel()
) {
    val state by viewModel.bootState.collectAsState()

    LaunchedEffect(state) {
        when (val s = state) {
            is BootState.RequireLogin -> onNavigateToLogin()
            is BootState.Authenticated -> onNavigateToRole(s.role)
            BootState.Loading -> { /* do nothing, show loader */ }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
