package com.yash.chargemeterpro.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yash.chargemeterpro.ui.LiveBatteryStateViewModel
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
fun ChargeMeterNavHost(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomNavItems.any { it.destination.route == currentRoute }

    // Shared across every top-level screen so the top bar's Share action
    // always has the current live snapshot to build a report from,
    // regardless of which tab is showing.
    val liveBatteryStateViewModel: LiveBatteryStateViewModel = hiltViewModel()
    val liveSnapshot by liveBatteryStateViewModel.snapshot.collectAsStateWithLifecycle()

    val topBarActionsViewModel: TopBarActionsViewModel = hiltViewModel()
    val context = LocalContext.current
    var showShareMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        topBarActionsViewModel.shareEvents.collect { uri ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share charging status"))
        }
    }

    Scaffold(
        topBar = {
            // Consistent ChargeFlow logo + name header, plus quick-action
            // icons, on every top-level (bottom-nav) screen. Drill-in
            // screens (Live Monitor, Battery Health, Session Detail, etc.)
            // render their own ScreenBackTopBar instead, since those need
            // a back action rather than these shortcuts.
            if (showBottomBar) {
                Box {
                    ChargeFlowTopBar(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = onToggleTheme,
                        onShare = { showShareMenu = true },
                        onOpenBatteryHealth = { navController.navigate(Destination.BatteryHealth.route) }
                    )
                    DropdownMenu(expanded = showShareMenu, onDismissRequest = { showShareMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Share as PDF") },
                            enabled = liveSnapshot != null,
                            onClick = {
                                showShareMenu = false
                                liveSnapshot?.let { topBarActionsViewModel.shareCurrentStatusAsPdf(it) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share as SVG") },
                            enabled = liveSnapshot != null,
                            onClick = {
                                showShareMenu = false
                                liveSnapshot?.let { topBarActionsViewModel.shareCurrentStatusAsSvg(it) }
                            }
                        )
                    }
                }
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
