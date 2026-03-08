package com.souigat.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.ui.navigation.AppNavGraph
import com.souigat.mobile.ui.theme.SouigatTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
        
        lifecycleScope.launch {
            tokenManager.onSessionCleared.collect {
                val intent = Intent(this@MainActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
        setContent {
            SouigatTheme {
                AppNavGraph()
            }
        }
    }
}
