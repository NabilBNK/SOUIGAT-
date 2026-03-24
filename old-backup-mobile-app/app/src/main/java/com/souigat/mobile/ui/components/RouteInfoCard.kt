package com.souigat.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Blue gradient card showing the conductor's current active route.
 * Displayed at the top of the Dashboard when a trip is in_progress.
 *
 * @param origin          Origin city/office name
 * @param destination     Destination city/office name
 * @param busPlate        Bus plate number
 * @param statusLabel     Status badge text (e.g. "En cours")
 */
@Composable
fun RouteInfoCard(
    origin: String,
    destination: String,
    busPlate: String,
    statusLabel: String = "En cours",
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val gradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF1D4ED8), Color(0xFF3B82F6)) // Blue 700 → Blue 500
    )

    val cardModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .let { base ->
            if (onClick != null) base.clickable(onClick = onClick) else base
        }
        .background(gradient)
        .padding(20.dp)

    Box(modifier = cardModifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Status badge
            Surface(
                color = Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = statusLabel,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Route row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = origin,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "vers",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = destination,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Bus plate
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsBus,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = busPlate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}
