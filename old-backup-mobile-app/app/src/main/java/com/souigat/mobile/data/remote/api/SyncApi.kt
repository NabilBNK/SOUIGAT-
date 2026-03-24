package com.souigat.mobile.data.remote.api

import com.souigat.mobile.data.remote.dto.SyncBatchRequest
import com.souigat.mobile.data.remote.dto.SyncBatchResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface SyncApi {

    @POST("sync/batch/")
    suspend fun syncBatch(
        @Body request: SyncBatchRequest
    ): Response<SyncBatchResponse>
}
