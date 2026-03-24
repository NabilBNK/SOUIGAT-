package com.souigat.mobile.debug

import androidx.room.withTransaction
import com.souigat.mobile.data.local.SouigatDatabase
import com.souigat.mobile.data.local.dao.CargoTicketDao
import com.souigat.mobile.data.local.dao.ExpenseDao
import com.souigat.mobile.data.local.dao.PassengerTicketDao
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.entity.CargoTicketEntity
import com.souigat.mobile.data.local.entity.ExpenseEntity
import com.souigat.mobile.data.local.entity.PassengerTicketEntity
import com.souigat.mobile.data.local.entity.TripEntity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class DebugProfileSeeder @Inject constructor(
    private val database: SouigatDatabase,
    private val tripDao: TripDao,
    private val passengerTicketDao: PassengerTicketDao,
    private val cargoTicketDao: CargoTicketDao,
    private val expenseDao: ExpenseDao
) {

    suspend fun seedHeavyDataset() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()

            val now = System.currentTimeMillis()
            val insertedTripIds = mutableListOf<Long>()

            database.withTransaction {
                buildOperationalTrips(now).forEach { trip ->
                    insertedTripIds += tripDao.upsert(trip)
                }
                buildHistoryTrips(now).forEach { trip ->
                    insertedTripIds += tripDao.upsert(trip)
                }

                val activeTripId = insertedTripIds.firstOrNull() ?: return@withTransaction
                passengerTicketDao.insertBatch(buildPassengerTickets(activeTripId, now))
                buildCargoTickets(activeTripId, now).forEach { cargoTicketDao.upsert(it) }
                buildExpenses(activeTripId, now).forEach { expenseDao.upsert(it) }
            }

            Timber.i(
                "DebugProfileSeeder seeded %d trips, %d passenger tickets, %d cargo tickets, %d expenses",
                insertedTripIds.size,
                PASSENGER_TICKET_COUNT,
                CARGO_TICKET_COUNT,
                EXPENSE_COUNT
            )
        }
    }

    private fun buildOperationalTrips(now: Long): List<TripEntity> {
        return buildList {
            repeat(OPERATIONAL_TRIP_COUNT) { index ->
                val route = ROUTES[index % ROUTES.size]
                val status = if (index == 0) "in_progress" else "scheduled"
                add(
                    TripEntity(
                        serverId = 900_000L + index,
                        originOffice = route.first,
                        destinationOffice = route.second,
                        conductorId = 16L,
                        busPlate = "16-${(index + 1).toString().padStart(5, '0')}-16",
                        status = status,
                        departureDateTime = now + (index * OPERATIONAL_TRIP_SPACING_MS),
                        passengerBasePrice = 2_500L + ((index % 5) * 250L),
                        cargoSmallPrice = 600L,
                        cargoMediumPrice = 900L,
                        cargoLargePrice = 1_300L,
                        currency = "DZD",
                        updatedAt = now - (index * 5_000L)
                    )
                )
            }
        }
    }

    private fun buildHistoryTrips(now: Long): List<TripEntity> {
        return buildList {
            val reversedRoutes = ROUTES.reversed()
            repeat(HISTORY_TRIP_COUNT) { index ->
                val route = reversedRoutes[index % reversedRoutes.size]
                val isCancelled = index % 4 == 0
                add(
                    TripEntity(
                        serverId = 910_000L + index,
                        originOffice = route.first,
                        destinationOffice = route.second,
                        conductorId = 16L,
                        busPlate = "26-${(index + 1).toString().padStart(5, '0')}-26",
                        status = if (isCancelled) "cancelled" else "completed",
                        departureDateTime = now - ((index + 1L) * HISTORY_TRIP_SPACING_MS),
                        passengerBasePrice = 2_000L + ((index % 6) * 300L),
                        cargoSmallPrice = 500L,
                        cargoMediumPrice = 800L,
                        cargoLargePrice = 1_100L,
                        currency = "DZD",
                        updatedAt = now - ((index + 1L) * 9_000L)
                    )
                )
            }
        }
    }

    private fun buildPassengerTickets(tripId: Long, now: Long): List<PassengerTicketEntity> {
        return buildList {
            repeat(PASSENGER_TICKET_COUNT) { index ->
                add(
                    PassengerTicketEntity(
                        serverId = 920_000L + index,
                        tripId = tripId,
                        ticketNumber = "P-DBG-${(index + 1).toString().padStart(4, '0')}",
                        idempotencyKey = "debug-passenger-$index",
                        passengerName = "Passager ${index + 1}",
                        price = 2_500L + ((index % 4) * 250L),
                        currency = "DZD",
                        paymentSource = if (index % 3 == 0) "prepaid" else "cash",
                        seatNumber = "S${(index % 52) + 1}",
                        status = if (index % 11 == 0) "cancelled" else "active",
                        createdAt = now - (index * 60_000L)
                    )
                )
            }
        }
    }

    private fun buildCargoTickets(tripId: Long, now: Long): List<CargoTicketEntity> {
        return buildList {
            repeat(CARGO_TICKET_COUNT) { index ->
                add(
                    CargoTicketEntity(
                        serverId = 930_000L + index,
                        tripId = tripId,
                        ticketNumber = "C-DBG-${(index + 1).toString().padStart(4, '0')}",
                        idempotencyKey = "debug-cargo-$index",
                        senderName = "Expediteur ${index + 1}",
                        senderPhone = "055000${index.toString().padStart(4, '0')}",
                        receiverName = "Destinataire ${index + 1}",
                        receiverPhone = "066000${index.toString().padStart(4, '0')}",
                        cargoTier = when (index % 3) {
                            0 -> "small"
                            1 -> "medium"
                            else -> "large"
                        },
                        description = "Colis debug ${index + 1}",
                        price = 900L + ((index % 3) * 300L),
                        currency = "DZD",
                        paymentSource = if (index % 2 == 0) "cash" else "prepaid",
                        status = when (index % 4) {
                            0 -> "created"
                            1 -> "loaded"
                            2 -> "in_transit"
                            else -> "arrived"
                        },
                        createdAt = now - (index * 90_000L)
                    )
                )
            }
        }
    }

    private fun buildExpenses(tripId: Long, now: Long): List<ExpenseEntity> {
        return buildList {
            repeat(EXPENSE_COUNT) { index ->
                val category = when (index % 4) {
                    0 -> "fuel"
                    1 -> "food"
                    2 -> "tolls"
                    else -> "other"
                }
                add(
                    ExpenseEntity(
                        serverId = 940_000L + index,
                        tripId = tripId,
                        idempotencyKey = "debug-expense-$index",
                        amount = 450L + ((index % 6) * 125L),
                        currency = "DZD",
                        category = category,
                        description = "Depense debug ${index + 1} - $category",
                        status = "active",
                        createdAt = now - (index * 120_000L)
                    )
                )
            }
        }
    }

    private companion object {
        const val OPERATIONAL_TRIP_COUNT = 140
        const val HISTORY_TRIP_COUNT = 140
        const val PASSENGER_TICKET_COUNT = 180
        const val CARGO_TICKET_COUNT = 120
        const val EXPENSE_COUNT = 90
        const val OPERATIONAL_TRIP_SPACING_MS = 45L * 60L * 1000L
        const val HISTORY_TRIP_SPACING_MS = 18L * 60L * 60L * 1000L

        val ROUTES = listOf(
            "Algiers Central" to "Oran Office",
            "Algiers Central" to "Annaba Office",
            "Oran Office" to "Setif Station",
            "Setif Station" to "Constantine Hub",
            "Annaba Office" to "Blida Depot",
            "Tlemcen Stop" to "Algiers Central",
            "Ghardaia Stop" to "Oran Office",
            "Bejaia Port" to "Setif Station"
        )
    }
}
