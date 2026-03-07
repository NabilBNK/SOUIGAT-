package com.souigat.mobile.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * All Room schema migrations.
 *
 * Add a new Migration(n, n+1) object here BEFORE bumping the database version in SouigatDatabase.
 * Without a matching migration, Room throws IllegalStateException on startup.
 *
 * This is intentional — we do NOT use fallbackToDestructiveMigration because
 * conductor offline data (tickets, expenses) must survive app updates.
 *
 * How to write a migration:
 *   val MIGRATION_1_2 = object : Migration(1, 2) {
 *       override fun migrate(db: SupportSQLiteDatabase) {
 *           db.execSQL("ALTER TABLE trips ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
 *       }
 *   }
 *
 * How to register it in DatabaseModule.kt:
 *   Room.databaseBuilder(...)
 *       .addMigrations(MIGRATION_1_2)
 *       .build()
 */
object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add idempotencyKey columns
            db.execSQL("ALTER TABLE passenger_tickets ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE cargo_tickets ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")

            // Add UNIQUE index on ticketNumber for both entities
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_passenger_tickets_ticketNumber ON passenger_tickets (ticketNumber)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_cargo_tickets_ticketNumber ON cargo_tickets (ticketNumber)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
