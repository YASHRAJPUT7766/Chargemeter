package com.yash.chargemeterpro.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yash.chargemeterpro.ui.components.ChargeFlowTopBar
import com.yash.chargemeterpro.ui.screens.about.AboutScreen
import com.yash.chargemeterpro.ui.screens.about.PrivacyPolicyScreen
import com.yash.chargemeterpro.ui.screens.batteryhealth.BatteryHealthScreen
import com.yash.chargemeterpro.ui.screens.history.CompareSessionsScreen
import com.yash.chargemeterpro.ui.screens.history.HistoryScreen
import com.yash.chargemeterpro.ui.screens.home.HomeScreen
import com.yash.chargemeterpro.ui.screens.livemonitor.LiveMonitorScreen
import com.yash.chargemeterpro.ui.screens.sessiondetail.SessionDetailScreen
import com.yash.chargemeterpro.ui.screens.settings.SettingsScreen
import com.yash.chargemeterpro.ui.screens.speedtest.SpeedTestScreen
import com.yash.chargemeterpro.ui.screens.statistics.StatisticsScreen

@Composable
fun ChargeMeterNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomNavItems.any { it.destination.route == currentRoute }

    Scaffold(
        topBar = {
            // Consistent ChargeFlow logo + name header on every top-level
            // (bottom-nav) screen. Drill-in screens (Live Monitor, Battery
            // Health, Session Detail, etc.) render their own
            // ScreenBackTopBar instead, since those need a back action.
            if (showBottomBar) {
                ChargeFlowTopBar()
            }
        },
        bottomBar = {
            if (showBottomBar) {
                ChargeMeterBottomBar(navController = navController, currentRoute = currentRoute)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.Home.route) {
                HomeScreen(
                    onNavigateToLiveMonitor = { navController.navigate(Destination.LiveMonitor.route) },
                    onNavigateToSpeedTest = { navController.navigate(Destination.SpeedTest.route) }
                )
            }
            composable(Destination.LiveMonitor.route) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ScreenBackTopBar(
                        title = "Live Monitor",
                        onBack = { navController.popBackStack() }
                    )
                    LiveMonitorScreen()
                }
            }
            composable(Destination.History.route) {
                HistoryScreen(
                    onOpenSession = { sessionId ->
                        navController.navigate(Destination.SessionDetail.createRoute(sessionId))
                    },
                    onCompareSessions = { navController.navigate(Destination.CompareSessions.route) }
                )
            }
            composable(Destination.BatteryHealth.route) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ScreenBackTopBar(
                        title = "Battery Health",
                        onBack = { navController.popBackStack() }
                    )
                    BatteryHealthScreen()
                }
            }
            composable(Destination.Statistics.route) {
                StatisticsScreen(
                    onNavigateToBatteryHealth = { navController.navigate(Destination.BatteryHealth.route) }
                )
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onNavigateToAbout = { navController.navigate(Destination.About.route) },
                    onNavigateToPrivacyPolicy = { navController.navigate(Destination.PrivacyPolicy.route) }
                )
            }
            composable(Destination.SpeedTest.route) {
                SpeedTestScreen(onBack = { navController.popBackStack() })
            }
            composable(Destination.About.route) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(Destination.PrivacyPolicy.route) {
                PrivacyPolicyScreen(onBack = { navController.popBackStack() })
            }
            composable(Destination.CompareSessions.route) {
                CompareSessionsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Destination.SessionDetail.route,
                arguments = listOf(navArgument(Destination.SessionDetail.ARG_SESSION_ID) {
                    type = androidx.navigation.NavType.LongType
                })
            ) { backStack ->
                val sessionId = backStack.arguments?.getLong(Destination.SessionDetail.ARG_SESSION_ID) ?: -1L
                SessionDetailScreen(sessionId = sessionId, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun ChargeMeterBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.destination.route,
                onClick = {
                    navController.navigate(item.destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}

/** Reusable top bar with a back button for drill-in / secondary screens. */
@Composable
fun ScreenBackTopBar(title: String, onBack: () -> Unit) {
    androidx.compose.material3.TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    )
}
