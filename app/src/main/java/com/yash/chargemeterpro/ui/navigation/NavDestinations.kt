package com.yash.chargemeterpro.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom-nav top-level destinations (spec: Home, Live Monitor, Charging
 * History, Battery Health, Statistics, Settings) plus secondary routes
 * reached by drilling in from those (session detail, speed test, about,
 * etc.), which intentionally do NOT get their own bottom-nav entry.
 */
sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object LiveMonitor : Destination("live_monitor")
    data object History : Destination("history")
    data object BatteryHealth : Destination("battery_health")
    data object Statistics : Destination("statistics")
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
}

data class BottomNavItem(
    val destination: Destination,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Destination.Home, "Home", Icons.Filled.Home),
    BottomNavItem(Destination.LiveMonitor, "Monitor", Icons.Filled.Bolt),
    BottomNavItem(Destination.History, "History", Icons.Filled.History),
    BottomNavItem(Destination.BatteryHealth, "Health", Icons.Filled.FavoriteBorder),
    BottomNavItem(Destination.Statistics, "Stats", Icons.Filled.BarChart),
    BottomNavItem(Destination.Settings, "Settings", Icons.Filled.Settings)
)
