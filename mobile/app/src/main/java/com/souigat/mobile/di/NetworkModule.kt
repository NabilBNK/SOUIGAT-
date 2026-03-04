package com.souigat.mobile.di

import com.souigat.mobile.BuildConfig
import com.souigat.mobile.data.remote.interceptor.AuthInterceptor
import com.souigat.mobile.data.remote.interceptor.TokenRefreshAuthenticator
import com.souigat.mobile.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
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

    @Provides
    @Singleton
    fun provideCertificatePinner(): CertificatePinner {
        return CertificatePinner.Builder()
            .apply {
                if (BuildConfig.BUILD_TYPE != "debug") {
                    // TODO P0.5: Replace placeholder pins with real SPKI hashes extracted via:
                    //   openssl s_client -connect staging.souigat.dz:443 -servername staging.souigat.dz < /dev/null 2>/dev/null \
                    //     | openssl x509 -pubkey -noout \
                    //     | openssl pkey -pubin -outform der \
                    //     | openssl dgst -sha256 -binary | base64
                    //
                    // Pin the INTERMEDIATE CA — not the leaf cert (survives cert renewal).
                    // Two pins = current + backup intermediate for rotation window.
                    //
                    // ⛔ DO NOT use these placeholder pins in a staging APK — it will throw
                    //    SSLPeerUnverifiedException on EVERY API call. All 8 devices will lose
                    //    connectivity simultaneously. Extract real pins first.
                    add(
                        "staging.souigat.dz",
                        "sha256/PLACEHOLDER_STAGING_INTERMEDIATE_PIN_1=",
                        "sha256/PLACEHOLDER_STAGING_BACKUP_PIN_2="
                    )
                    add(
                        "api.souigat.dz",
                        "sha256/PLACEHOLDER_PRODUCTION_INTERMEDIATE_PIN_1=",
                        "sha256/PLACEHOLDER_PRODUCTION_BACKUP_PIN_2="
                    )
                }
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenRefreshAuthenticator: TokenRefreshAuthenticator,
        certificatePinner: CertificatePinner
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(Constants.CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(Constants.READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(Constants.WRITE_TIMEOUT_S, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .authenticator(tokenRefreshAuthenticator)
            .apply {
                if (BuildConfig.BUILD_TYPE != "debug") {
                    certificatePinner(certificatePinner)
                }
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
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
}
