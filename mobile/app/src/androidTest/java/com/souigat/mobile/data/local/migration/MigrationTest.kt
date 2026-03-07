package com.souigat.mobile.data.local.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.souigat.mobile.data.local.SouigatDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SouigatDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        var db = helper.createDatabase(TEST_DB, 1)

        // db has schema version 1. insert some data using SQL queries.
        // We simulate a phase 3.3 corrupted duplicate ticket number scenario.
        db.execSQL("""
            INSERT INTO passenger_tickets (tripId, ticketNumber, passengerName, price, currency, paymentSource, seatNumber, status, createdAt) 
            VALUES (1, 'PT-20260307-0001', 'John Doe 1', 2000, 'DZD', 'cash', '1A', 'active', 1000)
        """.trimIndent())
        
        db.execSQL("""
            INSERT INTO passenger_tickets (tripId, ticketNumber, passengerName, price, currency, paymentSource, seatNumber, status, createdAt) 
            VALUES (1, 'PT-20260307-0001', 'John Doe 2 (Duplicate)', 2000, 'DZD', 'cash', '1B', 'active', 1001)
        """.trimIndent())

        db.execSQL("""
            INSERT INTO cargo_tickets (tripId, ticketNumber, senderName, senderPhone, receiverName, receiverPhone, cargoTier, description, price, currency, paymentSource, status, createdAt) 
            VALUES (1, 'CT-20260307-0001', 'Sender', '123', 'Receiver', '456', 'small', 'desc', 500, 'DZD', 'prepaid', 'created', 1000)
        """.trimIndent())

        db.close()

        // Re-open the database with version 2 and provide MIGRATION_1_2 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2)

        // Verify deduplication happened: only one PT-20260307-0001 should remain
        val cursor = db.query("SELECT * FROM passenger_tickets")
        assertEquals(1, cursor.count)
        cursor.moveToFirst()
        // Ensure idempotencyKey is populated
        val idempotencyKeyIndex = cursor.getColumnIndex("idempotencyKey")
        val idempotencyKey = cursor.getString(idempotencyKeyIndex)
        assert(idempotencyKey.isNotEmpty())
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3() {
        var db = helper.createDatabase(TEST_DB, 2)

        // Simulate an old Phase 3.0 expense without idempotencyKey (v2 schema)
        db.execSQL("""
            INSERT INTO expenses (tripId, amount, currency, category, description, createdAt) 
            VALUES (1, 500, 'DZD', 'food', 'Lunch', 2000)
        """.trimIndent())
        
        db.close()

        // Re-open with version 3 and apply MIGRATION_2_3
        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3)

        // Verify data survived and idempotencyKey was backfilled
        val cursor = db.query("SELECT * FROM expenses")
        assertEquals(1, cursor.count)
        cursor.moveToFirst()
        
        val amountIndex = cursor.getColumnIndex("amount")
        assertEquals(500L, cursor.getLong(amountIndex))

        val idempotencyKeyIndex = cursor.getColumnIndex("idempotencyKey")
        val idempotencyKey = cursor.getString(idempotencyKeyIndex)
        assert(idempotencyKey.isNotEmpty())
        // Backfill inserts exactly 32 hex chars (randomblob(16))
        assertEquals(32, idempotencyKey.length)
        
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4() {
        var db = helper.createDatabase(TEST_DB, 3)

        // Simulate a Phase 3.4 expense without status and unique index (v3 schema)
        db.execSQL("""
            INSERT INTO expenses (tripId, idempotencyKey, amount, currency, category, description, createdAt) 
            VALUES (1, '123e4567-e89b-12d3-a456-426614174000', 500, 'DZD', 'food', 'Lunch', 2000)
        """.trimIndent())
        
        db.close()

        // Re-open with version 4 and apply MIGRATION_3_4
        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, Migrations.MIGRATION_3_4)

        // Verify data survived and status was backfilled
        val cursor = db.query("SELECT * FROM expenses")
        assertEquals(1, cursor.count)
        cursor.moveToFirst()
        
        val statusIndex = cursor.getColumnIndex("status")
        val status = cursor.getString(statusIndex)
        assertEquals("active", status)
        
        cursor.close()

        // Verify unique index enforcement by attempting to insert a duplicate idempotency key
        var constraintFired = false
        try {
            db.execSQL("""
                INSERT INTO expenses (tripId, idempotencyKey, amount, currency, category, description, status, createdAt) 
                VALUES (1, '123e4567-e89b-12d3-a456-426614174000', 600, 'DZD', 'food', 'Dinner', 'active', 2005)
            """.trimIndent())
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            constraintFired = true
        }
        assert(constraintFired) { "Unique index on idempotencyKey was not enforced" }
    }
}
