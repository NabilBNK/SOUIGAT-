package com.souigat.mobile.data.remote.interceptor

import com.souigat.mobile.data.connectivity.BackendConnectionMonitor
import com.souigat.mobile.data.connectivity.BackendEndpointResolver
import java.io.IOException
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

class BackendStatusInterceptor @Inject constructor(
    private val backendConnectionMonitor: BackendConnectionMonitor,
    private val backendEndpointResolver: BackendEndpointResolver
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        return try {
            val response = chain.proceed(chain.request())
            if (response.code < 500) {
                backendConnectionMonitor.markBackendSuccess()
            } else {
                backendConnectionMonitor.markBackendFailure()
            }
            response
        } catch (error: IOException) {
            backendEndpointResolver.invalidateResolvedBaseUrl()
            backendConnectionMonitor.markBackendFailure()
            throw error
        }
    }
}
