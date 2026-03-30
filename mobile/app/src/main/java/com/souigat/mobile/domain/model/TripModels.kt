package com.souigat.mobile.domain.model

data class TripListItem(
    val id: Long,
    val origin: String,
    val destination: String,
    val conductorName: String,
    val plate: String,
    val departureDatetime: String,
    val status: String,
    val passengerBasePrice: Long,
    val cargoSmallPrice: Long? = null,
    val cargoMediumPrice: Long? = null,
    val cargoLargePrice: Long? = null,
    val currency: String,
)

data class TripRouteStop(
    val officeId: Int,
    val officeName: String,
    val stopOrder: Int,
)

data class TripRouteSegmentTariff(
    val fromStopOrder: Int,
    val toStopOrder: Int,
    val passengerPrice: Long,
    val currency: String,
)

data class TripDetail(
    val id: Long,
    val originOfficeId: Int,
    val destinationOfficeId: Int,
    val conductorId: Int,
    val busId: Int,
    val departureDatetime: String,
    val arrivalDatetime: String? = null,
    val status: String,
    val passengerBasePrice: Long,
    val cargoSmallPrice: Long,
    val cargoMediumPrice: Long,
    val cargoLargePrice: Long,
    val currency: String,
    val conductorName: String,
    val busPlate: String,
    val originName: String,
    val destinationName: String,
    val routeTemplateName: String = "",
    val routeStops: List<TripRouteStop> = emptyList(),
    val routeSegmentTariffs: List<TripRouteSegmentTariff> = emptyList(),
)

data class TripCompletionRecap(
    val passengerCashTotal: Long,
    val cargoCashTotal: Long,
    val expensesTotal: Long,
    val cashExpected: Long,
    val passengerCount: Int,
    val cargoCount: Int,
    val currency: String,
)

data class TripStatusResult(
    val status: String,
    val completionRecap: TripCompletionRecap? = null,
)
