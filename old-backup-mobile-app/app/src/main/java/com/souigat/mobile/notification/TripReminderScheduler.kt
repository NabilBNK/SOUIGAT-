package com.souigat.mobile.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.souigat.mobile.data.local.dao.TripDao
import com.souigat.mobile.data.local.entity.TripEntity
import com.souigat.mobile.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tripDao: TripDao
) {

    suspend fun rescheduleFromDatabase() {
        syncTrips(tripDao.getAllNow())
    }

    fun syncTrips(trips: List<TripEntity>) {
        trips.forEach(::syncTrip)
    }

    fun syncTrip(trip: TripEntity) {
        val tripKey = trip.serverId ?: trip.id
        if (trip.status != "scheduled") {
            cancelReminderSet(tripKey)
            return
        }

        scheduleReminder(
            trip = trip,
            tripKey = tripKey,
            reminderType = TripReminderWorker.REMINDER_ONE_DAY,
            triggerAtMillis = trip.departureDateTime - TimeUnit.DAYS.toMillis(1)
        )
        scheduleReminder(
            trip = trip,
            tripKey = tripKey,
            reminderType = TripReminderWorker.REMINDER_TWO_HOURS,
            triggerAtMillis = trip.departureDateTime - TimeUnit.HOURS.toMillis(2)
        )
        scheduleReminder(
            trip = trip,
            tripKey = tripKey,
            reminderType = TripReminderWorker.REMINDER_START,
            triggerAtMillis = trip.departureDateTime
        )
    }

    private fun scheduleReminder(
        trip: TripEntity,
        tripKey: Long,
        reminderType: String,
        triggerAtMillis: Long
    ) {
        val workName = buildWorkName(tripKey, reminderType)
        val delay = triggerAtMillis - System.currentTimeMillis()
        if (delay <= 0L) {
            WorkManager.getInstance(context).cancelUniqueWork(workName)
            return
        }

        val request = OneTimeWorkRequestBuilder<TripReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    TripReminderWorker.KEY_TRIP_LOCAL_ID to trip.id,
                    TripReminderWorker.KEY_REMINDER_TYPE to reminderType
                )
            )
            .addTag(Constants.TRIP_REMINDER_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun cancelReminderSet(tripKey: Long) {
        reminderTypes.forEach { reminderType ->
            WorkManager.getInstance(context).cancelUniqueWork(buildWorkName(tripKey, reminderType))
        }
    }

    private fun buildWorkName(tripKey: Long, reminderType: String): String {
        return "trip_reminder_${tripKey}_$reminderType"
    }

    companion object {
        private val reminderTypes = listOf(
            TripReminderWorker.REMINDER_ONE_DAY,
            TripReminderWorker.REMINDER_TWO_HOURS,
            TripReminderWorker.REMINDER_START
        )
    }
}
