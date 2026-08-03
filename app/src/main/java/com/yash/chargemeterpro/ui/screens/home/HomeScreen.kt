package com.yash.chargemeterpro.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.model.ChargingStatus
import com.yash.chargemeterpro.domain.usecase.ChargeTimeEstimator
import com.yash.chargemeterpro.domain.usecase.ChargingSpeed
import com.yash.chargemeterpro.domain.usecase.PowerTerminology
import com.yash.chargemeterpro.ui.LiveBatteryStateViewModel
import com.yash.chargemeterpro.ui.components.DisclaimerText
import com.yash.chargemeterpro.ui.components.GaugeZone
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.components.NeedleGauge
import com.yash.chargemeterpro.ui.components.ReadingRow
import com.yash.chargemeterpro.ui.components.RingMeter
import com.yash.chargemeterpro.ui.components.WattMeterGauge
import com.yash.chargemeterpro.ui.theme.GraphTemperature
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PhosphorGreen
import com.yash.chargemeterpro.ui.theme.SpeedFast
import com.yash.chargemeterpro.ui.theme.SpeedNormal
import com.yash.chargemeterpro.ui.theme.SpeedSlow
import com.yash.chargemeterpro.ui.theme.SpeedTrickle
import com.yash.chargemeterpro.ui.theme.VoltageBlue
import com.yash.chargemeterpro.ui.theme.WarningAmber

/**
 * The Home screen deliberately mirrors the exact layout sketched in the
 * product spec:
 *
 *   CHARGING
 *   82%
 *   ⚡ 18.4 W
 *   Current: 3200 mA
 *   Voltage: 5.75 V
 *   Temperature: 32°C
 *   Estimated time remaining: 28 min
 *   Charging Speed: FAST
 *
 * — expressed through the app's instrument-panel component system
 * (WattMeterGauge as the "⚡ 18.4 W" hero, ReadingRow for the mA/V/°C
 * lines) rather than literal plain-text lines, while keeping that exact
 * information hierarchy: status+percent first, power second, supporting
 * readings third, time/speed last.
 */
@Composable
fun HomeScreen(
    onNavigateToLiveMonitor: () -> Unit,
    onNavigateToSpeedTest: () -> Unit,
    liveState: LiveBatteryStateViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val snapshot by liveState.snapshot.collectAsStateWithLifecycle()
    val timeEstimate by liveState.timeEstimate.collectAsStateWithLifecycle()
    val chargingSpeed by liveState.chargingSpeed.collectAsStateWithLifecycle()
    val sessionCount by homeViewModel.completedSessionCount.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroWattCard(
                snapshot = snapshot,
                timeEstimate = timeEstimate,
                chargingSpeed = chargingSpeed,
                onOpenLiveMonitor = onNavigateToLiveMonitor
            )
        }

        if (snapshot?.isCharging != true) {
            item { NotChargingPrompt() }
        }

        item {
            QuickActionsRow(
                onSpeedTest = onNavigateToSpeedTest,
                onLiveMonitor = onNavigateToLiveMonitor
            )
        }

        item {
            TodayStrip(sessionCount = sessionCount)
        }

        item {
            DisclaimerText(text = PowerTerminology.WATTAGE_ESTIMATE_DISCLAIMER)
        }
    }
}

@Composable
private fun HeroWattCard(
    snapshot: BatterySnapshot?,
    timeEstimate: ChargeTimeEstimator.TimeEstimate,
    chargingSpeed: ChargingSpeed,
    onOpenLiveMonitor: () -> Unit
) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            StatusBadge(status = snapshot?.chargingStatus ?: ChargingStatus.UNKNOWN)

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${snapshot?.batteryPercent ?: "--"}%",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            val powerWatts = snapshot?.let {
                com.yash.chargemeterpro.domain.usecase.PowerCalculator.batteryInputPowerWatts(it)
            }
            NeedleGauge(
                fraction = ((powerWatts ?: 0.0) / 30.0).toFloat(),
                value = powerWatts?.let { "%.1f".format(it) } ?: "—",
                unit = "WATTS",
                minLabel = "0",
                maxLabel = "30W",
                zones = listOf(
                    GaugeZone(0f, 0.25f, WarningAmber),
                    GaugeZone(0.25f, 0.6f, VoltageBlue),
                    GaugeZone(0.6f, 1f, PhosphorGreen)
                ),
                needleColor = PhosphorGreen,
                modifier = Modifier.fillMaxWidth(0.85f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RingMeter(
                    fraction = ((snapshot?.currentMilliAmpsNormalized ?: 0.0) / 3000.0).toFloat(),
                    value = snapshot?.currentMilliAmpsNormalized?.let { "%.0f".format(it) } ?: "—",
                    unit = "mA",
                    color = WarningAmber,
                    size = 82.dp,
                    strokeWidth = 7.dp
                )
                RingMeter(
                    fraction = ((snapshot?.voltageVolts ?: 0.0) / 5.0).toFloat(),
                    value = snapshot?.voltageVolts?.let { "%.2f".format(it) } ?: "—",
                    unit = "V",
                    color = VoltageBlue,
                    size = 82.dp,
                    strokeWidth = 7.dp
                )
                RingMeter(
                    fraction = ((snapshot?.temperatureC ?: 0.0) / 45.0).toFloat(),
                    value = snapshot?.temperatureC?.let { "%.1f".format(it) } ?: "—",
                    unit = "°C",
                    color = GraphTemperature,
                    size = 82.dp,
                    strokeWidth = 7.dp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Time remaining", style = MaterialTheme.typography.bodySmall, color = PanelGray)
                    Text(
                        text = if (snapshot?.isCharging == true) {
                            HomeFormatters.minutesToReadable(timeEstimate.minutesRemainingToFull)
                        } else "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Charging speed", style = MaterialTheme.typography.bodySmall, color = PanelGray)
                    SpeedChip(speed = chargingSpeed)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onOpenLiveMonitor, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Live Monitor")
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ChargingStatus) {
    val (label, color) = when (status) {
        ChargingStatus.CHARGING -> "CHARGING" to PhosphorGreen
        ChargingStatus.DISCHARGING -> "DISCHARGING" to VoltageBlue
        ChargingStatus.NOT_CHARGING -> "NOT CHARGING" to PanelGray
        ChargingStatus.FULL -> "FULL" to PhosphorGreen
        ChargingStatus.UNKNOWN -> "UNKNOWN" to PanelGray
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SpeedChip(speed: ChargingSpeed) {
    val (label, color) = when (speed) {
        ChargingSpeed.FAST -> "FAST" to SpeedFast
        ChargingSpeed.NORMAL -> "NORMAL" to SpeedNormal
        ChargingSpeed.SLOW -> "SLOW" to SpeedSlow
        ChargingSpeed.TRICKLE -> "TRICKLE" to SpeedTrickle
        ChargingSpeed.UNKNOWN -> "—" to PanelGray
    }
    Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
}

@Composable
private fun NotChargingPrompt() {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                "Not currently charging",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Plug in to see live charging data, or check the Battery Health and Statistics tabs for your device diagnostics and drain history.",
                style = MaterialTheme.typography.bodyMedium,
                color = PanelGray
            )
        }
    }
}

@Composable
private fun QuickActionsRow(onSpeedTest: () -> Unit, onLiveMonitor: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onSpeedTest,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Speed Test")
        }
        OutlinedButton(onClick = onLiveMonitor, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Live Graphs")
        }
    }
}

@Composable
private fun TodayStrip(sessionCount: Int) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RingMeter(
                    fraction = (sessionCount / 100f), // 100-session reference ceiling
                    value = "$sessionCount",
                    unit = "SESSIONS",
                    color = PhosphorGreen,
                    size = 64.dp,
                    strokeWidth = 6.dp
                )
                Column {
                    Text("All-time sessions", style = MaterialTheme.typography.bodySmall, color = PanelGray)
                    Text(
                        "$sessionCount total",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                "View History →",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
