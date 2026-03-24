package com.souigat.mobile.di

import com.souigat.mobile.BuildConfig
import com.souigat.mobile.data.remote.interceptor.AuthInterceptor
import com.souigat.mobile.data.remote.interceptor.BackendStatusInterceptor
import com.souigat.mobile.data.remote.interceptor.DynamicBaseUrlInterceptor
import com.souigat.mobile.data.remote.interceptor.TokenRefreshAuthenticator
import com.souigat.mobile.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // TODO P0: Add CertificatePinner with real SPKI hashes before production release.
    // Extract hashes via:
    //   openssl s_client -connect api.souigat.dz:443 -servername api.souigat.dz < /dev/null 2>/dev/null \
    //     | openssl x509 -pubkey -noout \
    //     | openssl pkey -pubin -outform der \
    //     | openssl dgst -sha256 -binary | base64
    // Pin the INTERMEDIATE CA — not the leaf cert (survives cert renewal).

    @Provides
    @Singleton
    fun provideOkHttpClient(
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
        authInterceptor: AuthInterceptor,
        backendStatusInterceptor: BackendStatusInterceptor,
        tokenRefreshAuthenticator: TokenRefreshAuthenticator
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(Constants.CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(Constants.READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(Constants.WRITE_TIMEOUT_S, TimeUnit.SECONDS)
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(backendStatusInterceptor)
            .authenticator(tokenRefreshAuthenticator)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        }
                    )
                }
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideSyncApi(retrofit: Retrofit): com.souigat.mobile.data.remote.api.SyncApi {
        return retrofit.create(com.souigat.mobile.data.remote.api.SyncApi::class.java)
    }
}
