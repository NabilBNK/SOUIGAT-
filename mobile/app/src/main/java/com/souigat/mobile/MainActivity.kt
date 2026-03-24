package com.souigat.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.souigat.mobile.BuildConfig
import com.souigat.mobile.debug.DebugProfileSeeder
import com.souigat.mobile.ui.navigation.AppNavGraph
import com.souigat.mobile.ui.theme.SouigatTheme
import dagger.hilt.android.AndroidEntryPoint
import com.souigat.mobile.data.local.TokenManager
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * Single-activity Compose host.
 *
 * @AndroidEntryPoint is REQUIRED for Hilt to inject ViewModels into this activity.
 * Removing it causes: "Hilt Activity must extend ComponentActivity or FragmentActivity"
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var debugProfileSeeder: DebugProfileSeeder

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("debugSeedProfile", false)) {
            runBlocking {
                debugProfileSeeder.seedHeavyDataset()
            }
        }
        enableEdgeToEdge()
        val debugStartRoute = if (BuildConfig.DEBUG) {
            intent.getStringExtra("debugRoute")
        } else {
            null
        }
        setContent {
            SouigatTheme {
                AppNavGraph(
                    tokenManager = tokenManager,
                    debugStartRoute = debugStartRoute
                )
            }
        }
        window.decorView.post {
            requestNotificationPermissionIfNeeded()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val prefs = getSharedPreferences("souigat_runtime_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("notifications_prompted", false)) {
            return
        }

        prefs.edit().putBoolean("notifications_prompted", true).apply()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
