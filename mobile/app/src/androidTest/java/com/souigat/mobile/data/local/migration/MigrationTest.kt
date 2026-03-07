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
        SouigatDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        var db = helper.createDatabase(TEST_DB, 1)

        // db has schema version 1. insert some data using SQL queries.
        // We simulate a phase 3.3 corrupted duplicate ticket number scenario.
        db.execSQL("""
            INSERT INTO passenger_tickets (tripId, ticketNumber, passengerName, price, currency, paymentSource, status, createdAt) 
            VALUES (1, 'PT-20260307-0001', 'John Doe 1', 2000, 'DZD', 'cash', 'active', 1000)
        """.trimIndent())
        
        db.execSQL("""
            INSERT INTO passenger_tickets (tripId, ticketNumber, passengerName, price, currency, paymentSource, status, createdAt) 
            VALUES (1, 'PT-20260307-0001', 'John Doe 2 (Duplicate)', 2000, 'DZD', 'cash', 'active', 1001)
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
}
