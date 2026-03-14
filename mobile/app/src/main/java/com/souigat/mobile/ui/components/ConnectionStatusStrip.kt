package com.souigat.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.souigat.mobile.data.connectivity.BackendConnectionState
import com.souigat.mobile.ui.theme.ErrorRed
import com.souigat.mobile.ui.theme.Success
import com.souigat.mobile.ui.theme.Warning

@Composable
fun ConnectionStatusStrip(
    state: BackendConnectionState,
    modifier: Modifier = Modifier
) {
    val (icon, label, containerColor, contentColor) = when (state) {
        BackendConnectionState.Online -> ConnectionStripStyle(
            icon = Icons.Default.Wifi,
            label = "Connecte au serveur",
            containerColor = Success.copy(alpha = 0.14f),
            contentColor = Success
        )

        BackendConnectionState.Checking -> ConnectionStripStyle(
            icon = Icons.Default.Sync,
            label = "Verification reseau",
            containerColor = Warning.copy(alpha = 0.14f),
            contentColor = Warning
        )

        BackendConnectionState.BackendUnavailable -> ConnectionStripStyle(
            icon = Icons.Default.CloudQueue,
            label = "Serveur indisponible",
            containerColor = ErrorRed.copy(alpha = 0.12f),
            contentColor = ErrorRed
        )

        BackendConnectionState.Offline -> ConnectionStripStyle(
            icon = Icons.Default.CloudOff,
            label = "Mode hors ligne",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = containerColor,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
        }
    }
}

private data class ConnectionStripStyle(
    val icon: ImageVector,
    val label: String,
    val containerColor: Color,
    val contentColor: Color
)
