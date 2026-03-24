package com.souigat.mobile.di

import com.souigat.mobile.data.remote.api.TripApi
import com.souigat.mobile.data.repository.TripRepositoryImpl
import com.souigat.mobile.domain.repository.TripRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TripApiModule {
    
    @Provides
    @Singleton
    fun provideTripApi(retrofit: Retrofit): TripApi {
        return retrofit.create(TripApi::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TripRepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindTripRepository(
        tripRepositoryImpl: TripRepositoryImpl
    ): TripRepository
}
