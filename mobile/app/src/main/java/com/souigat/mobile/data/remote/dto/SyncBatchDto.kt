package com.souigat.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SyncBatchRequest(
    @SerialName("trip_id") val tripId: Long,
    @SerialName("resume_from") val resumeFrom: Long = 0,
    @SerialName("items") val items: List<SyncItemDto>
)

@Serializable
data class SyncItemDto(
    @SerialName("type") val type: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("local_id") val localId: Long,
    @SerialName("payload") val payload: JsonElement
)

@Serializable
data class SyncBatchResponse(
    @SerialName("items") val items: List<SyncItemResponse>
)

@Serializable
data class SyncItemResponse(
    @SerialName("local_id") val localId: Long?,
    @SerialName("status") val status: String
)
