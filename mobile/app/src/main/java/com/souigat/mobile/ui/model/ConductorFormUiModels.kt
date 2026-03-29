package com.souigat.mobile.ui.model

import androidx.compose.runtime.Immutable

@Immutable
data class TripFormHeaderUiModel(
    val tripId: Long,
    val origin: String,
    val destination: String,
    val busPlate: String,
    val departureLabel: String,
    val currency: String,
    val statusLabel: String
)

@Immutable
data class TicketPriceOptionUiModel(
    val valueCentimes: Long,
    val label: String
)

@Immutable
data class CargoTierPriceUiModel(
    val tier: String,
    val displayName: String,
    val label: String,
    val amountLabel: String,
    val valueCentimes: Long
)
