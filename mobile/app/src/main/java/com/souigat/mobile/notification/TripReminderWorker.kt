package com.souigat.mobile.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.souigat.mobile.data.local.dao.TripDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TripReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tripDao: TripDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val tripLocalId = inputData.getLong(KEY_TRIP_LOCAL_ID, -1L)
        val reminderType = inputData.getString(KEY_REMINDER_TYPE) ?: return Result.failure()
        if (tripLocalId <= 0L) {
            return Result.failure()
        }

        val trip = tripDao.getById(tripLocalId) ?: return Result.success()
        if (trip.status != "scheduled") {
            return Result.success()
        }

        TripReminderNotifier.showReminder(
            context = applicationContext,
            trip = trip,
            reminderType = reminderType
        )
        return Result.success()
    }

    companion object {
        const val KEY_TRIP_LOCAL_ID = "trip_local_id"
        const val KEY_REMINDER_TYPE = "reminder_type"

        const val REMINDER_ONE_DAY = "one_day"
        const val REMINDER_TWO_HOURS = "two_hours"
        const val REMINDER_START = "start"
    }
}
