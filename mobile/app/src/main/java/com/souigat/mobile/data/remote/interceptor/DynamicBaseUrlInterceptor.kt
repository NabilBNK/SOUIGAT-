package com.souigat.mobile.data.remote.interceptor

import com.souigat.mobile.data.connectivity.BackendEndpointResolver
import com.souigat.mobile.util.Constants
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class DynamicBaseUrlInterceptor @Inject constructor(
    private val backendEndpointResolver: BackendEndpointResolver
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!Constants.usesDynamicDebugBackend()) {
            return chain.proceed(chain.request())
        }

        val targetBaseUrl = backendEndpointResolver.getActiveBaseUrl().toHttpUrl()
        val originalRequest = chain.request()
        val rewrittenUrl = originalRequest.url.newBuilder()
            .scheme(targetBaseUrl.scheme)
            .host(targetBaseUrl.host)
            .port(targetBaseUrl.port)
            .build()

        return chain.proceed(
            originalRequest.newBuilder()
                .url(rewrittenUrl)
                .build()
        )
    }
}
