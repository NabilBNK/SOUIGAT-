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

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add idempotencyKey column safely
            db.execSQL("ALTER TABLE expenses ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")
            
            // Backfill with random UUID-equivalent hex to satisfy unique/idempotency requirements
            db.execSQL("UPDATE expenses SET idempotencyKey = lower(hex(randomblob(16))) WHERE idempotencyKey = ''")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add status column for soft-cancel
            db.execSQL("ALTER TABLE expenses ADD COLUMN status TEXT NOT NULL DEFAULT 'active'")
            // Add DB-level uniqueness enforcement for idempotencyKey
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_expenses_idempotencyKey ON expenses (idempotencyKey)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_passenger_tickets_createdAt ON passenger_tickets (createdAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cargo_tickets_createdAt ON cargo_tickets (createdAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_createdAt ON expenses (createdAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_trips_departureDateTime ON trips (departureDateTime)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_trips_status_departureDateTime ON trips (status, departureDateTime)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sync_queue ADD COLUMN nextAttemptAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE sync_queue ADD COLUMN lastAttemptAt INTEGER")
            db.execSQL("ALTER TABLE sync_queue ADD COLUMN lastErrorCode TEXT")
            db.execSQL("ALTER TABLE sync_queue ADD COLUMN lastErrorMessage TEXT")
            db.execSQL("ALTER TABLE sync_queue ADD COLUMN deadLetterReason TEXT")

            db.execSQL("UPDATE sync_queue SET nextAttemptAt = createdAt WHERE nextAttemptAt = 0")

            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_status_nextAttemptAt ON sync_queue (status, nextAttemptAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_tripId_createdAt ON sync_queue (tripId, createdAt)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
