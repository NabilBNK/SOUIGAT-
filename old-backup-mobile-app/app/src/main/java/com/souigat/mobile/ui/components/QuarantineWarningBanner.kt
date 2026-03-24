package com.souigat.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.souigat.mobile.ui.theme.Warning
import com.souigat.mobile.ui.theme.WarningContainer

/**
 * Non-dismissable amber warning banner shown on Dashboard and Profile
 * when the conductor has quarantined tickets that require admin attention.
 *
 * Per spec: this banner CANNOT be dismissed by the user.
 *
 * @param quarantinedCount Number of quarantined items (must be > 0 before showing)
 */
@Composable
fun QuarantineWarningBanner(
    quarantinedCount: Int,
    modifier: Modifier = Modifier
) {
    if (quarantinedCount <= 0) return

    val plural = if (quarantinedCount > 1) "billets" else "billet"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color = WarningContainer, shape = MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Avertissement",
            tint = Warning,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$quarantinedCount $plural en quarantaine",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF92400E) // Amber 800 for high contrast on WarningContainer
            )
            Text(
                text = "Contactez votre administrateur pour résoudre ce problème.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF78350F) // Amber 900
            )
        }
    }
}
