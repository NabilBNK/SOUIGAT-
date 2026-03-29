package com.souigat.mobile.di

import android.content.Context
import com.souigat.mobile.data.firebase.FirebaseSessionManager
import com.souigat.mobile.data.local.SouigatDatabase
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.repository.AuthRepositoryImpl
import com.souigat.mobile.domain.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager {
        return TokenManager(context)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        tokenManager: TokenManager,
        database: SouigatDatabase,
        firebaseSessionManager: FirebaseSessionManager,
    ): AuthRepository = AuthRepositoryImpl(
        tokenManager,
        database,
        firebaseSessionManager,
    )
}

