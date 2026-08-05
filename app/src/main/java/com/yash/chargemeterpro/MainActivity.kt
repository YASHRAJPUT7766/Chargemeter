package com.yash.chargemeterpro

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.yash.chargemeterpro.ui.components.NotificationPermissionRequester
import com.yash.chargemeterpro.ui.navigation.ChargeMeterNavHost
import com.yash.chargemeterpro.ui.theme.ChargeMeterProTheme
import com.yash.chargemeterpro.util.DrainMonitorWorkScheduler
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yash.chargemeterpro.util.UsageWidgetUpdateWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var drainMonitorWorkScheduler: DrainMonitorWorkScheduler
    @Inject lateinit var usageWidgetWorkScheduler: UsageWidgetWorkScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() so the system draws the
        // splash window immediately on cold start, straight from local
        // resources. This system-level splash is now intentionally very
        // brief — just long enough to avoid a blank-window flash while
        // Compose spins up. The *real* branded splash experience (progress
        // bar, "Loading...", logo animation) is rendered in Compose by
        // OnboardingSplashStage / OnboardingScreen once ChargeMeterNavHost
        // decides which flow to show, based on onboardingComplete from
        // SettingsDataStore.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val splashStartMillis = SystemClock.elapsedRealtime()
        val minimumSplashDurationMillis = 150L
        splashScreen.setKeepOnScreenCondition {
            SystemClock.elapsedRealtime() - splashStartMillis < minimumSplashDurationMillis
        }

        // Ensure the drain-monitor background job is scheduled from first
        // launch, independent of whether Always-On Monitor is enabled —
        // Battery Statistics' drain-rate figures need this regardless.
        drainMonitorWorkScheduler.schedule()
        usageWidgetWorkScheduler.schedule()
        // Kick an immediate one-off run too — enqueueUniquePeriodicWork's
        // first execution isn't guaranteed instant, so without this a
        // freshly-placed UsageWidget could sit on "—" for a while after
        // first launch/reboot.
        WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<UsageWidgetUpdateWorker>().build())

        setContent {
            val viewModel: com.yash.chargemeterpro.ui.MainActivityViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val themeMode by viewModel.themeMode.collectAsState(initial = "dark")
            val useDynamicColor by viewModel.useDynamicColor.collectAsState(initial = false)
            val onboardingComplete by viewModel.onboardingComplete.collectAsState(initial = null)

            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            ChargeMeterProTheme(darkTheme = darkTheme, useDynamicColor = useDynamicColor) {
                NotificationPermissionRequester()
                ChargeMeterNavHost(
                    isDarkTheme = darkTheme,
                    onToggleTheme = { viewModel.toggleTheme(currentlyResolvedDark = darkTheme) },
                    onboardingComplete = onboardingComplete,
                    onOnboardingFinished = { viewModel.markOnboardingComplete() }
                )
            }
        }
    }
}
