package com.souigat.mobile.ui.model

data class TripFormHeaderUiModel(
    val tripId: Long,
    val origin: String,
    val destination: String,
    val busPlate: String,
    val departureLabel: String,
    val currency: String,
    val statusLabel: String
)

data class TicketPriceOptionUiModel(
    val valueCentimes: Long,
    val label: String
)

data class CargoTierPriceUiModel(
    val tier: String,
    val label: String,
    val valueCentimes: Long
)
