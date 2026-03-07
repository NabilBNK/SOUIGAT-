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
            // Add idempotencyKey columns safely
            db.execSQL("ALTER TABLE passenger_tickets ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE cargo_tickets ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")

            // Backfill with random UUID-equivalent hex to satisfy unique/idempotency requirements
            db.execSQL("UPDATE passenger_tickets SET idempotencyKey = lower(hex(randomblob(16))) WHERE idempotencyKey = ''")
            db.execSQL("UPDATE cargo_tickets SET idempotencyKey = lower(hex(randomblob(16))) WHERE idempotencyKey = ''")

            // Deduplicate Phase 3.3 corruptions (REPLACE bug) before binding UNIQUE index
            db.execSQL("""
                DELETE FROM passenger_tickets 
                WHERE id NOT IN (SELECT MIN(id) FROM passenger_tickets GROUP BY ticketNumber)
            """.trimIndent())
            db.execSQL("""
                DELETE FROM cargo_tickets 
                WHERE id NOT IN (SELECT MIN(id) FROM cargo_tickets GROUP BY ticketNumber)
            """.trimIndent())

            // Add UNIQUE index on ticketNumber for both entities dropping duplicates at DB level
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_passenger_tickets_ticketNumber ON passenger_tickets (ticketNumber)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_cargo_tickets_ticketNumber ON cargo_tickets (ticketNumber)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
