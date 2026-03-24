package com.souigat.mobile.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.souigat.mobile.ui.theme.ErrorRed
import com.souigat.mobile.ui.theme.Success
import com.souigat.mobile.ui.theme.Warning

/**
 * Small pill badge reflecting the current sync queue state.
 *
 * States:
 * - pendingCount == 0 && quarantinedCount == 0 → "✓ Synchronisé" in green
 * - quarantinedCount > 0                       → "⚠ X en quarantaine" in red
 * - pendingCount > 0                           → "X en attente" in amber
 */
@Composable
fun SyncStatusBadge(
    pendingCount: Int,
    quarantinedCount: Int,
    modifier: Modifier = Modifier
) {
    val (text, color) = when {
        quarantinedCount > 0 -> "⚠ $quarantinedCount en quarantaine" to ErrorRed
        pendingCount > 0     -> "$pendingCount en attente" to Warning
        else                 -> "✓ Synchronisé" to Success
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}
