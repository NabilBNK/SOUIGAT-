package com.souigat.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TripListDto(
    val id: Int,
    val origin: String,
    val destination: String,
    val conductor: String,
    val plate: String,
    @SerialName("departure_datetime")
    val departureDatetime: String,
    val status: String,
    @SerialName("passenger_base_price")
    val passengerBasePrice: Long,
    val currency: String
)

@Serializable
data class TripDetailDto(
    val id: Int,
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
    val arrivalDatetime: String? = null
)
