package com.souigat.mobile

import android.app.Application
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application entry point.
 *
 * @HiltAndroidApp is REQUIRED — Hilt generates the component hierarchy from this class.
 * Removing it will cause RuntimeException on launch:
 *   "Hilt components have not been installed in Application"
 */
@HiltAndroidApp
class SouigatApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Timber: structured debug logging — stripped in release by ProGuard via Timber.DebugTree check
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Firebase: crash reporting — always initialized (Crashlytics is no-op in debug builds)
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Timber.e(e, "Firebase initialization failed. Using placeholder google-services.json?")
        }
    }
}
