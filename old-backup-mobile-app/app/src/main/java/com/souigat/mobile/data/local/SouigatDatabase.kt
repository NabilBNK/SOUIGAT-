package com.souigat.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.souigat.mobile.data.local.dao.*
import com.souigat.mobile.data.local.entity.*

/**
 * Main Room database.
 *
 * version = 1: starting version. Increment with each schema change.
 * exportSchema = true: writes schema JSON to /schemas/ for MigrationTestHelper.
 *                      Required by Room's built-in migration verification tools.
 *
 * ⛔ NO fallbackToDestructiveMigration — EVER.
 * If a migration is missing, Room throws IllegalStateException at startup.
 * This is intentional: data loss is unacceptable (conductor's offline tickets).
 * Always add a Migration(n, n+1) object in Migrations.kt before version bump.
 */
@Database(
    entities = [
        TripEntity::class,
        PassengerTicketEntity::class,
        CargoTicketEntity::class,
        ExpenseEntity::class,
        SyncQueueEntity::class,
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(SyncStatusConverter::class)
abstract class SouigatDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun passengerTicketDao(): PassengerTicketDao
    abstract fun cargoTicketDao(): CargoTicketDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        const val DATABASE_NAME = "souigat.db"
    }
}
