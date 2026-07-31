package com.yash.chargemeterpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.yash.chargemeterpro.ui.components.NotificationPermissionRequester
import com.yash.chargemeterpro.ui.navigation.ChargeMeterNavHost
import com.yash.chargemeterpro.ui.theme.ChargeMeterProTheme
import com.yash.chargemeterpro.util.DrainMonitorWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var drainMonitorWorkScheduler: DrainMonitorWorkScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure the drain-monitor background job is scheduled from first
        // launch, independent of whether Always-On Monitor is enabled —
        // Battery Statistics' drain-rate figures need this regardless.
        drainMonitorWorkScheduler.schedule()

        setContent {
            val viewModel: com.yash.chargemeterpro.ui.MainActivityViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val themeMode by viewModel.themeMode.collectAsState(initial = "dark")
            val useDynamicColor by viewModel.useDynamicColor.collectAsState(initial = false)

            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            ChargeMeterProTheme(darkTheme = darkTheme, useDynamicColor = useDynamicColor) {
                NotificationPermissionRequester()
                ChargeMeterNavHost()
            }
        }
    }
}
