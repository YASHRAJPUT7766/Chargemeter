package com.yash.chargemeterpro.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.components.SettingsSwitchRow
import com.yash.chargemeterpro.ui.theme.PanelGray

@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
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
        item { AlertsSection(viewModel) }
        item { ThresholdsSection(viewModel) }
        item { MonitoringSection(viewModel) }
        item { PrivacySection(viewModel) }
        item { AboutSection(onNavigateToAbout, onNavigateToPrivacyPolicy) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = PanelGray,
        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
    )
}

@Composable
private fun AppearanceSection(vm: SettingsViewModel) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by vm.useDynamicColor.collectAsStateWithLifecycle()

    Column {
        SectionHeader("Appearance")
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
        SectionHeader("Smart Charging Alerts")
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

    Column {
        SectionHeader("Custom Thresholds")
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val isIgnoringBatteryOptimizations by vm.isIgnoringBatteryOptimizations.collectAsStateWithLifecycle()

    Column {
        SectionHeader("Monitoring")
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
                        "Android's default battery-saving rules can occasionally block charging monitoring from starting automatically in the background. Exempting ChargeFlow from battery optimization makes auto-start monitoring and alerts fire reliably.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PanelGray
                    )
                    androidx.compose.material3.OutlinedButton(
                        onClick = { vm.requestIgnoreBatteryOptimizations(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Allow ChargeFlow to run in the background")
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacySection(vm: SettingsViewModel) {
    val cloudBackup by vm.cloudBackupEnabled.collectAsStateWithLifecycle()
    val analytics by vm.analyticsConsent.collectAsStateWithLifecycle()

    Column {
        SectionHeader("Privacy")
        InstrumentCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    "All charging history is stored only on this device by default. Nothing is uploaded unless you enable an option below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PanelGray
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)
                SettingsSwitchRow(
                    title = "Cloud backup",
                    subtitle = "Off by default. When enabled, charging history syncs to your account.",
                    checked = cloudBackup,
                    onCheckedChange = vm::setCloudBackupEnabled
                )
                SettingsSwitchRow(
                    title = "Anonymous usage analytics",
                    subtitle = "Off by default. Helps improve the app — never includes personal charging data.",
                    checked = analytics,
                    onCheckedChange = vm::setAnalyticsConsent
                )
            }
        }
    }
}

@Composable
private fun AboutSection(onNavigateToAbout: () -> Unit, onNavigateToPrivacyPolicy: () -> Unit) {
    Column {
        SectionHeader("About")
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
