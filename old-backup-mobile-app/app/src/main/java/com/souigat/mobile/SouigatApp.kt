package com.souigat.mobile

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.souigat.mobile.notification.TripReminderNotifier
import com.souigat.mobile.notification.TripReminderScheduler
import com.souigat.mobile.worker.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Application entry point.
 */
@HiltAndroidApp
class SouigatApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var tripReminderScheduler: TripReminderScheduler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Timber.e(e, "Firebase initialization failed.")
        }

        TripReminderNotifier.createChannels(this)

        // Idempotent schedule (KEEP policy): safe at every app launch.
        syncScheduler.schedulePeriodicSync()
        applicationScope.launch {
            tripReminderScheduler.rescheduleFromDatabase()
        }
    }
}
