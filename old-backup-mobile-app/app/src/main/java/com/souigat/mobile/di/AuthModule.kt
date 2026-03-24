package com.souigat.mobile.di

import android.content.Context
import com.souigat.mobile.BuildConfig
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.remote.api.AuthApi
import com.souigat.mobile.data.repository.AuthRepositoryImpl
import com.souigat.mobile.domain.repository.AuthRepository
import com.souigat.mobile.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    @Named("auth") // Pure retrofit without custom interceptors/authenticators
    fun provideAuthRetrofit(
        json: Json
    ): Retrofit {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(Constants.CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(Constants.READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(Constants.WRITE_TIMEOUT_S, TimeUnit.SECONDS)
            .apply {
                // TODO P0: Re-add CertificatePinner when real SPKI hashes are available
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .build()
            
        return Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(@Named("auth") retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager {
        return TokenManager(context)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        authApi: AuthApi,
        tokenManager: TokenManager
    ): AuthRepository = AuthRepositoryImpl(authApi, tokenManager)
}
