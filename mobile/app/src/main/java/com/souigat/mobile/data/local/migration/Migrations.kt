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
    // No migrations needed for version=1 (initial schema).
    // Add Migration(1, 2), Migration(2, 3), etc. here as the schema evolves.
    val ALL: Array<Migration> = emptyArray()
}
