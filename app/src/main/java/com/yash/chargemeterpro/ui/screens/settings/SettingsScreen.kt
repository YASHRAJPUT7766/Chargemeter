package com.yash.chargemeterpro.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.components.SettingsSwitchRow
import com.yash.chargemeterpro.ui.theme.GraphTemperature
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PhosphorGreen
import com.yash.chargemeterpro.ui.theme.VoltageBlue
import com.yash.chargemeterpro.ui.theme.WarningAmber

@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBatteryOptimizationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item { AppearanceSection(viewModel) }
        item { HistorySection(onNavigateToHistory) }
        item { AlertsSection(viewModel) }
        item { ThresholdsSection(viewModel) }
        item { MonitoringSection(viewModel) }
        item { PrivacySection(viewModel) }
        item { AboutSection(onNavigateToAbout, onNavigateToPrivacyPolicy) }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        }
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = PanelGray
        )
    }
}

@Composable
private fun AppearanceSection(vm: SettingsViewModel) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by vm.useDynamicColor.collectAsStateWithLifecycle()

    Column {
        SectionHeader("Appearance", Icons.Filled.Palette, VoltageBlue)
        InstrumentCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Theme", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEach { (value, label) ->
                        FilterChip(
                            selected = themeMode == value,
                            onClick = { vm.setThemeMode(value) },
                            label = { Text(label) }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
                SettingsSwitchRow(
                    title = "Use Material You dynamic color",
                    subtitle = "Overrides the instrument-panel accent palette with colors from your wallpaper",
                    checked = dynamicColor,
                    onCheckedChange = vm::setUseDynamicColor
                )
            }
        }
    }
}

/**
 * History moved here from the bottom nav bar (spec item #5) — full
 * charging history is reached from Settings, in addition to the
 * shortcut cards on Home and Stats.
 */
@Composable
private fun HistorySection(onNavigateToHistory: () -> Unit) {
    Column {
        SectionHeader("History", Icons.Filled.History, PhosphorGreen)
        InstrumentCard(modifier = Modifier.fillMaxWidth()) {
            NavRow(title = "Charging History", onClick = onNavigateToHistory)
        }
    }
}

@Composable
private fun AlertsSection(vm: SettingsViewModel) {
    val chargingStarted by vm.alertChargingStarted.collectAsStateWithLifecycle()
    val chargingCompleted by vm.alertChargingCompleted.collectAsStateWithLifecycle()
    val pct80 by vm.alert80Percent.collectAsStateWithLifecycle()
    val pct90 by vm.alert90Percent.collectAsStateWithLifecycle()
    val pct100 by vm.alert100Percent.collectAsStateWithLifecycle()
    val highTemp by vm.alertHighTemp.collectAsStateWithLifecycle()
    val slowCharge by vm.alertSlowCharging.collectAsStateWithLifecycle()
    val disconnected by vm.alertDisconnected.collectAsStateWithLifecycle()
    val criticalLow by vm.alertCriticalLow.collectAsStateWithLifecycle()

    Column {
        SectionHeader("Smart Charging Alerts", Icons.Filled.NotificationsActive, WarningAmber)
        InstrumentCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingsSwitchRow("Charging started", checked = chargingStarted, onCheckedChange = vm::setAlertChargingStarted)
                SettingsSwitchRow("Charging completed", checked = chargingCompleted, onCheckedChange = vm::setAlertChargingCompleted)
                SettingsSwitchRow("Battery reaches 80%", checked = pct80, onCheckedChange = vm::setAlert80Percent)
                SettingsSwitchRow("Battery reaches 90%", checked = pct90, onCheckedChange = vm::setAlert90Percent)
                SettingsSwitchRow("Battery reaches 100%", checked = pct100, onCheckedChange = vm::setAlert100Percent)
                SettingsSwitchRow("Temperature unusually high", checked = highTemp, onCheckedChange = vm::setAlertHighTemp)
                SettingsSwitchRow("Charging unusually slow", checked = slowCharge, onCheckedChange = vm::setAlertSlowCharging)
                SettingsSwitchRow("Charging disconnected early", checked = disconnected, onCheckedChange = vm::setAlertDisconnected)
                SettingsSwitchRow("Battery critically low", checked = criticalLow, onCheckedChange = vm::setAlertCriticalLow)
            }
        }
    }
}

@Composable
private fun ThresholdsSection(vm: SettingsViewModel) {
    val highTempThreshold by vm.highTempThreshold.collectAsStateWithLifecycle()
    val criticalLowThreshold by vm.criticalLowThreshold.collectAsStateWithLifecycle()
    val slowChargeThreshold by vm.slowChargeThreshold.collectAsStateWithLifecycle()
    val customMilestoneEnabled by vm.customMilestoneEnabled.collectAsStateWithLifecycle()
    val savedCustomMilestone by vm.customMilestone.collectAsStateWithLifecycle()

    // Local, uncommitted slider value — the user drags this freely and
    // it only gets written to SettingsDataStore (and therefore only
    // takes effect for the monitor service) when they tap Save. Without
    // this, dragging the slider silently persisted a value on every
    // pixel of movement with no clear "did my change actually apply?"
    // moment, which is what made this feel broken before.
    var pendingMilestone by androidx.compose.runtime.remember(savedCustomMilestone) {
        androidx.compose.runtime.mutableStateOf(savedCustomMilestone.toFloat())
    }
    val hasUnsavedChange = pendingMilestone.toInt() != savedCustomMilestone

    Column {
        SectionHeader("Custom Thresholds", Icons.Filled.Tune, GraphTemperature)
        InstrumentCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ThresholdSlider(
                    label = "High temperature alert",
                    valueText = "%.0f°C".format(highTempThreshold),
                    value = highTempThreshold,
                    range = 35f..55f,
                    onValueChange = vm::setHighTempThreshold
                )
                ThresholdSlider(
                    label = "Critical low battery alert",
                    valueText = "$criticalLowThreshold%",
                    value = criticalLowThreshold.toFloat(),
                    range = 5f..30f,
                    onValueChange = { vm.setCriticalLowThreshold(it.toInt()) }
                )
                ThresholdSlider(
                    label = "Slow charging alert threshold",
                    valueText = "%.1fW".format(slowChargeThreshold),
                    value = slowChargeThreshold,
                    range = 1f..15f,
                    onValueChange = vm::setSlowChargeThreshold
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                SettingsSwitchRow(
                    title = "Stop-charging reminder",
                    subtitle = "Battery Stats can't stop charging by itself — Android doesn't allow that — but it will keep reminding you to unplug once you cross this limit",
                    checked = customMilestoneEnabled,
                    onCheckedChange = vm::setCustomMilestoneEnabled
                )

                if (customMilestoneEnabled) {
                    ThresholdSlider(
                        label = "Remind me to unplug at",
                        valueText = "${pendingMilestone.toInt()}%",
                        value = pendingMilestone,
                        range = 50f..100f,
                        onValueChange = { pendingMilestone = it }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        androidx.compose.material3.Button(
                            onClick = { vm.setCustomMilestone(pendingMilestone.toInt()) },
                            enabled = hasUnsavedChange
                        ) {
                            Text(if (hasUnsavedChange) "Save" else "Saved")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(valueText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun MonitoringSection(vm: SettingsViewModel) {
    val alwaysOn by vm.alwaysOnMonitorEnabled.collectAsStateWithLifecycle()
    val autoStart by vm.autoStartMonitoring.collectAsStateWithLifecycle()
    val screenOnStats by vm.screenOnStatsEnabled.collectAsStateWithLifecycle()
    val chargingDisplay by vm.chargingDisplayEnabled.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isIgnoringBatteryOptimizations by vm.isIgnoringBatteryOptimizations.collectAsStateWithLifecycle()

    Column {
        SectionHeader("Monitoring", Icons.Filled.Timer, PhosphorGreen)
        InstrumentCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingsSwitchRow(
                    title = "Always On Charging Monitor",
                    subtitle = "Runs a persistent background service so charging data and alerts keep updating even when the app is closed",
                    checked = alwaysOn,
                    onCheckedChange = vm::setAlwaysOnMonitorEnabled
                )
                SettingsSwitchRow(
                    title = "Auto-start monitoring on charge",
                    subtitle = "Automatically create a session the moment a charger is connected",
                    checked = autoStart,
                    onCheckedChange = vm::setAutoStartMonitoring
                )
                SettingsSwitchRow(
                    title = "Screen-on charging statistics",
                    subtitle = "Track whether the screen was on during charging samples",
                    checked = screenOnStats,
                    onCheckedChange = vm::setScreenOnStatsEnabled
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                SettingsSwitchRow(
                    title = "Charging Display (lock screen)",
                    subtitle = "Shows a full-screen animated charging readout over your lock screen every time you plug in — different from Always On Charging Monitor above, which just runs quietly in the background",
                    checked = chargingDisplay,
                    onCheckedChange = vm::setChargingDisplayEnabled
                )
            }
        }

        if (!isIgnoringBatteryOptimizations) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
            InstrumentCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Improve background reliability",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Android's default battery-saving rules can occasionally block charging monitoring from starting automatically in the background. Exempting Battery Stats from battery optimization makes auto-start monitoring and alerts fire reliably.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PanelGray
                    )
                    androidx.compose.material3.OutlinedButton(
                        onClick = { vm.requestIgnoreBatteryOptimizations(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Allow Battery Stats to run in the background")
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacySection(vm: SettingsViewModel) {
    Column {
        SectionHeader("Privacy", Icons.Filled.Shield, VoltageBlue)
        InstrumentCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    "All charging history is stored only on this device. Battery Stats has no server and no internet permission — nothing you do here is ever uploaded anywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PanelGray
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)
                // Cloud backup and analytics are intentionally NOT shown as
                // live toggles here. There's no backend wired up yet (no
                // Firebase, no INTERNET permission in the manifest) — a
                // toggle that saves a flag but does nothing behind it is
                // worse than no toggle at all, because it looks like it
                // works. These come back once real sync/analytics exist.
                Text(
                    "Cloud backup and usage analytics: coming in a future update.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PanelGray
                )
            }
        }
    }
}

@Composable
private fun AboutSection(onNavigateToAbout: () -> Unit, onNavigateToPrivacyPolicy: () -> Unit) {
    Column {
        SectionHeader("About", Icons.Filled.Info, PanelGray)
        InstrumentCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                NavRow(title = "About ChargeMeter Pro", onClick = onNavigateToAbout)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                NavRow(title = "Privacy Policy", onClick = onNavigateToPrivacyPolicy)
            }
        }
    }
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = PanelGray)
    }
}
