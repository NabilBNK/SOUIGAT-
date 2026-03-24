package com.souigat.mobile.di

import android.content.Context
import androidx.room.Room
import com.souigat.mobile.data.local.SouigatDatabase
import com.souigat.mobile.data.local.dao.*
import com.souigat.mobile.data.local.migration.Migrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SouigatDatabase {
        return Room.databaseBuilder(
            context,
            SouigatDatabase::class.java,
            SouigatDatabase.DATABASE_NAME
        )
            .addMigrations(*Migrations.ALL)
            // ⛔ fallbackToDestructiveMigration INTENTIONALLY OMITTED — data loss is unacceptable
            .build()
    }

    @Provides
    fun provideTripDao(db: SouigatDatabase): TripDao = db.tripDao()

    @Provides
    fun providePassengerTicketDao(db: SouigatDatabase): PassengerTicketDao = db.passengerTicketDao()

    @Provides
    fun provideCargoTicketDao(db: SouigatDatabase): CargoTicketDao = db.cargoTicketDao()

    @Provides
    fun provideExpenseDao(db: SouigatDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideSyncQueueDao(db: SouigatDatabase): SyncQueueDao = db.syncQueueDao()
}
