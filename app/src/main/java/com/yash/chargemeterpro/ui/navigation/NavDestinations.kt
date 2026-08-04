package com.yash.chargemeterpro.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom-nav top-level destinations — exactly 4: Home, Stats, Usage,
 * Settings. History is no longer a bottom-nav destination (moved into
 * Settings, with shortcuts from Home/Stats — see spec item #5); its
 * slot in the bar is replaced by the new Usage dashboard (spec item #6).
 *
 * Live Monitor and Battery Health are NOT deleted — all of their existing
 * functionality (graphs, charger analysis, health scoring) is fully
 * intact, they're just reached as drill-ins instead of getting their own
 * bottom-nav slot: Live Monitor from Home's "Open Live Monitor" action,
 * Battery Health from a card on the Stats screen.
 */
sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object LiveMonitor : Destination("live_monitor")
    data object History : Destination("history")
    data object BatteryHealth : Destination("battery_health")
    data object Statistics : Destination("statistics")
    data object Usage : Destination("usage")
    data object Settings : Destination("settings")

    // Secondary / drill-in routes
    data object SpeedTest : Destination("speed_test")
    data object About : Destination("about")
    data object PrivacyPolicy : Destination("privacy_policy")
    data object CompareSessions : Destination("compare_sessions")

    data object SessionDetail : Destination("session_detail/{sessionId}") {
        fun createRoute(sessionId: Long) = "session_detail/$sessionId"
        const val ARG_SESSION_ID = "sessionId"
    }

    data object AppDetail : Destination("app_detail/{packageName}") {
        fun createRoute(packageName: String) = "app_detail/${android.net.Uri.encode(packageName)}"
        const val ARG_PACKAGE_NAME = "packageName"
    }
}

data class BottomNavItem(
    val destination: Destination,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Destination.Home, "Home", Icons.Filled.Home),
    BottomNavItem(Destination.Statistics, "Stats", Icons.Filled.BarChart),
    BottomNavItem(Destination.Usage, "Usage", Icons.Filled.DonutLarge),
    BottomNavItem(Destination.Settings, "Settings", Icons.Filled.Settings)
)
