package com.souigat.mobile.ui.screens.boot

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BootScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRole: (String) -> Unit,
    viewModel: BootViewModel = hiltViewModel()
) {
    val state by viewModel.bootState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        when (val current = state) {
            is BootState.RequireLogin -> onNavigateToLogin()
            is BootState.Authenticated -> onNavigateToRole(current.role)
            BootState.Loading -> Unit
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "boot_progress")
    val translationRatio by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "boot_progress_translation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "SOUIGAT",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .width(156.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.10f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.34f)
                        .offset(x = 156.dp * translationRatio)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "PREPARATION DE VOTRE SESSION TERRAIN",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF616A75)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYSTEM_LOAD: STABLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4B535D)
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF4B535D))
                )
                Text(
                    text = "CORE_V2.4.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4B535D)
                )
            }
            Spacer(modifier = Modifier.height(44.dp))
        }
    }
}
