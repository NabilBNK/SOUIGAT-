package com.souigat.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.souigat.mobile.ui.navigation.AppNavGraph
import com.souigat.mobile.ui.theme.SouigatTheme
import dagger.hilt.android.AndroidEntryPoint
import com.souigat.mobile.data.local.TokenManager
import javax.inject.Inject

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SouigatTheme {
                AppNavGraph(tokenManager = tokenManager)
            }
        }
    }
}
