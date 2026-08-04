package com.yash.chargemeterpro.ui.navigation

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.yash.chargemeterpro.ui.screens.appdetail.AppDetailScreen
import com.yash.chargemeterpro.ui.screens.batteryhealth.BatteryHealthScreen
import com.yash.chargemeterpro.ui.screens.history.CompareSessionsScreen
import com.yash.chargemeterpro.ui.screens.history.HistoryScreen
import com.yash.chargemeterpro.ui.screens.home.HomeScreen
import com.yash.chargemeterpro.ui.screens.livemonitor.LiveMonitorScreen
import com.yash.chargemeterpro.ui.screens.onboarding.OnboardingScreen
import com.yash.chargemeterpro.ui.screens.onboarding.OnboardingSplashStage
import com.yash.chargemeterpro.ui.screens.sessiondetail.SessionDetailScreen
import com.yash.chargemeterpro.ui.screens.settings.SettingsScreen
import com.yash.chargemeterpro.ui.screens.speedtest.SpeedTestScreen
import com.yash.chargemeterpro.ui.screens.statistics.StatisticsScreen
import com.yash.chargemeterpro.ui.screens.usage.UsageScreen
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PhosphorGreen

@Composable
fun ChargeMeterNavHost(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onboardingComplete: Boolean? = true,
    onOnboardingFinished: () -> Unit = {}
) {
    // Gate the entire app behind the onboarding flag before anything else
    // renders.
    //
    //   - onboardingComplete == null: SettingsDataStore hasn't emitted its
    //     first value yet (this frame only, right at cold start). Show
    //     just the branded splash — not the full feature list — so a
    //     returning user never sees onboarding flash before we know the
    //     real flag.
    //   - onboardingComplete == false (first ever launch): branded splash
    //     -> feature list -> "Get Started" -> flips the flag to true ->
    //     recomposes straight past this gate into the normal app below.
    //   - onboardingComplete == true (every later cold start): branded
    //     splash plays once more as a fixed-duration loading beat, then
    //     goes straight to Home — the feature list never shows again.
    if (onboardingComplete == null) {
        OnboardingSplashStage(onFinished = {})
        return
    }

    if (!onboardingComplete) {
        var hasFinishedOnboardingFlow by remember { mutableStateOf(false) }
        if (!hasFinishedOnboardingFlow) {
            OnboardingScreen(onGetStarted = {
                hasFinishedOnboardingFlow = true
                onOnboardingFinished()
            })
            return
        }
    }

    var showReturningSplash by remember { mutableStateOf(onboardingComplete) }
    if (showReturningSplash) {
        OnboardingSplashStage(onFinished = { showReturningSplash = false })
        return
    }

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
                    onNavigateToSpeedTest = { navController.navigate(Destination.SpeedTest.route) },
                    onNavigateToHistory = {
                        // History is now a drill-in reached from a shortcut
                        // (Home card / Stats card / Settings row), not a
                        // bottom-nav destination — so this is a plain forward
                        // navigation with a real back stack entry, not the
                        // popUpTo-to-graph-root + saveState/restoreState
                        // pattern used for switching between bottom-nav tabs
                        // below in ChargeMeterBottomBar.
                        navController.navigate(Destination.History.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Destination.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
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
                Column(modifier = Modifier.fillMaxSize()) {
                    ScreenBackTopBar(
                        title = "Charging History",
                        onBack = { navController.popBackStack() }
                    )
                    HistoryScreen(
                        onOpenSession = { sessionId ->
                            navController.navigate(Destination.SessionDetail.createRoute(sessionId))
                        },
                        onCompareSessions = { navController.navigate(Destination.CompareSessions.route) }
                    )
                }
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
                    onNavigateToBatteryHealth = { navController.navigate(Destination.BatteryHealth.route) },
                    onNavigateToHistory = { navController.navigate(Destination.History.route) }
                )
            }
            composable(Destination.Usage.route) {
                UsageScreen(
                    onOpenApp = { packageName ->
                        navController.navigate(Destination.AppDetail.createRoute(packageName))
                    }
                )
            }
            composable(
                route = Destination.AppDetail.route,
                arguments = listOf(navArgument(Destination.AppDetail.ARG_PACKAGE_NAME) {
                    type = androidx.navigation.NavType.StringType
                })
            ) {
                AppDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onNavigateToAbout = { navController.navigate(Destination.About.route) },
                    onNavigateToPrivacyPolicy = { navController.navigate(Destination.PrivacyPolicy.route) },
                    onNavigateToHistory = { navController.navigate(Destination.History.route) }
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

/**
 * Floating capsule nav bar, matching the ChargeFlow reference: the whole
 * bar sits as one rounded, elevated pill with margin around it (not an
 * edge-to-edge bar with a top divider line), and the active tab gets its
 * own smaller green-tinted pill inside that. Previously this used a
 * full-width bar with a 1px divider line and no outer rounding —
 * visually flat/edge-to-edge rather than the floating-capsule look in the
 * design reference.
 *
 * Uses MaterialTheme.colorScheme.surfaceVariant rather than a hardcoded
 * dark-only color, so the capsule itself switches from the dark
 * "elevated card" tone to the light scheme's white/near-white surface
 * automatically when the user toggles Light mode — previously this was
 * hardcoded to InstrumentSurfaceRaised (a fixed dark color from
 * DarkInstrumentScheme), so the bar stayed black even in Light mode.
 */
@Composable
private fun ChargeMeterBottomBar(navController: NavHostController, currentRoute: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.destination.route
            BottomNavPillItem(
                icon = item.icon,
                label = item.label,
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

/**
 * A single bottom-nav destination, styled to match the ChargeFlow spec:
 * the active tab gets a soft green pill behind its icon+label and both
 * turn phosphor green; inactive tabs stay plain panel-gray. Shown
 * identically on every top-level screen (Home, Stats, History, Settings)
 * since ChargeMeterBottomBar lives in the shared Scaffold.
 */
@Composable
private fun BottomNavPillItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) PhosphorGreen else PanelGray
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) PhosphorGreen.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Reusable top bar with a back button for drill-in / secondary screens
 * (Live Monitor, Battery Health, About, Privacy Policy, Speed Test,
 * Compare Sessions, Session Detail).
 *
 * Also shows the ChargeFlow logo next to the title, matching
 * ChargeFlowTopBar on the top-level screens (Home/Stats/History/
 * Settings) — previously this bar only had the back arrow and title, so
 * the brand mark disappeared the moment the user navigated one level
 * deep into the app, which read as inconsistent/unbranded on every
 * screen except those four.
 */
@Composable
fun ScreenBackTopBar(title: String, onBack: () -> Unit) {
    androidx.compose.material3.TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(
                        id = com.yash.chargemeterpro.R.drawable.ic_launcher_foreground
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Text(title, modifier = Modifier.padding(start = 10.dp))
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    )
}
