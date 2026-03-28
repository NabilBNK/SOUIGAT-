package com.souigat.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TripListDto(
    val id: Long,
    val origin: String,
    val destination: String,
    val conductor: String,
    val plate: String,
    @SerialName("departure_datetime")
    val departureDatetime: String,
    val status: String,
    @SerialName("passenger_base_price")
    val passengerBasePrice: Long,
    @SerialName("cargo_small_price")
    val cargoSmallPrice: Long? = null,
    @SerialName("cargo_medium_price")
    val cargoMediumPrice: Long? = null,
    @SerialName("cargo_large_price")
    val cargoLargePrice: Long? = null,
    val currency: String
)

@Serializable
data class TripDetailDto(
    val id: Long,
    @SerialName("origin_office")
    val originOffice: Int,
    @SerialName("destination_office")
    val destinationOffice: Int,
    val conductor: Int,
    val bus: Int,
    @SerialName("departure_datetime")
    val departureDatetime: String,
    @SerialName("arrival_datetime")
    val arrivalDatetime: String? = null,
    val status: String,
    @SerialName("passenger_base_price")
    val passengerBasePrice: Long,
    @SerialName("cargo_small_price")
    val cargoSmallPrice: Long,
    @SerialName("cargo_medium_price")
    val cargoMediumPrice: Long,
    @SerialName("cargo_large_price")
    val cargoLargePrice: Long,
    val currency: String,
    @SerialName("conductor_name")
    val conductorName: String,
    @SerialName("bus_plate")
    val busPlate: String,
    @SerialName("origin_name")
    val originName: String,
    @SerialName("destination_name")
    val destinationName: String
)

@Serializable
data class TripStatusDto(
    val status: String,
    @SerialName("arrival_datetime")
    val arrivalDatetime: String? = null,
    @SerialName("settlement_preview")
    val settlementPreview: SettlementPreviewDto? = null,
    @SerialName("settlement_preview_error")
    val settlementPreviewError: String? = null
)

@Serializable
data class SettlementPreviewDto(
    @SerialName("settlement_id")
    val settlementId: Int,
    val status: String,
    @SerialName("office_name")
    val officeName: String,
    @SerialName("expected_total_cash")
    val expectedTotalCash: Long,
    @SerialName("expenses_to_reimburse")
    val expensesToReimburse: Long,
    @SerialName("net_cash_expected")
    val netCashExpected: Long,
    @SerialName("agency_presale_total")
    val agencyPresaleTotal: Long,
    @SerialName("outstanding_cargo_delivery")
    val outstandingCargoDelivery: Long
)
