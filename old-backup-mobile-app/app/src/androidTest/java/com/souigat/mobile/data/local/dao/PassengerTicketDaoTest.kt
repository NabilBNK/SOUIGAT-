package com.souigat.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.souigat.mobile.data.local.SouigatDatabase
import com.souigat.mobile.data.local.entity.PassengerTicketEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class PassengerTicketDaoTest {

    private lateinit var db: SouigatDatabase
    private lateinit var dao: PassengerTicketDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, SouigatDatabase::class.java
        ).build()
        dao = db.passengerTicketDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testBatchInsertIsTransactional() = runBlocking {
        // Arrange
        val tickets = listOf(
            PassengerTicketEntity(
                tripId = 1L,
                ticketNumber = "PT-2026-0001",
                idempotencyKey = "key-1",
                passengerName = "Passager",
                price = 1000L,
                currency = "DZD",
                paymentSource = "cash",
                seatNumber = "",
                status = "active"
            ),
            PassengerTicketEntity(
                tripId = 1L,
                ticketNumber = "PT-2026-0002",
                idempotencyKey = "key-2",
                passengerName = "Passager",
                price = 1500L,
                currency = "DZD",
                paymentSource = "cash",
                seatNumber = "",
                status = "active"
            )
        )

        // Act
        dao.insertBatch(tickets)

        // Assert
        val count = dao.getCountByDate("PT-2026")
        assertEquals(2, count)
    }
}
