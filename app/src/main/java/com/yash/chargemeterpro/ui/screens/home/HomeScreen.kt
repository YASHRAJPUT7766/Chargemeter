package com.yash.chargemeterpro.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShowChart
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.chargemeterpro.domain.model.AvailableOr
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.model.ChargingStatus
import com.yash.chargemeterpro.domain.usecase.ChargeTimeEstimator
import com.yash.chargemeterpro.domain.usecase.ChargingSpeed
import com.yash.chargemeterpro.domain.usecase.PowerCalculator
import com.yash.chargemeterpro.domain.usecase.PowerTerminology
import com.yash.chargemeterpro.ui.LiveBatteryStateViewModel
import com.yash.chargemeterpro.ui.components.CompactMeterGauge
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.components.RingMeter
import com.yash.chargemeterpro.ui.components.SparklineGraph
import com.yash.chargemeterpro.ui.theme.GraphBatteryPct
import com.yash.chargemeterpro.ui.theme.Hairline
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PanelGrayDim
import com.yash.chargemeterpro.ui.theme.PhosphorGreen
import com.yash.chargemeterpro.ui.theme.PhosphorGreenDim
import com.yash.chargemeterpro.ui.theme.VoltageBlue
import com.yash.chargemeterpro.ui.theme.VoltageBlueDim
import com.yash.chargemeterpro.ui.theme.WarningAmber

/**
 * The Home screen — laid out to match the ChargeFlow product screenshot
 * exactly:
 *
 *   [NOT CHARGING]                 (glowing battery jar)
 *   70%
 *   Battery Level
 *
 *   WATT | CURRENT | VOLTAGE | TEMPERATURE
 *
 *   Charging Speed | Time Remaining   (each with a mini trend line)
 *
 *   "Monitor Everything in Real-time" -> Open Live Monitor
 *
 *   Speed Test        Live Graphs
 *
 *   All-time Sessions -> View History
 *
 *   Wattage disclaimer
 *
 * All values are driven by the same live snapshot / view-model wiring
 * as before — only the presentation changed.
 */
@Composable
fun HomeScreen(
    onNavigateToLiveMonitor: () -> Unit,
    onNavigateToSpeedTest: () -> Unit,
    onNavigateToCheckup: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    liveState: LiveBatteryStateViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val snapshot by liveState.snapshot.collectAsStateWithLifecycle()
    val timeEstimate by liveState.timeEstimate.collectAsStateWithLifecycle()
    val chargingSpeed by liveState.chargingSpeed.collectAsStateWithLifecycle()
    val sessionCount by homeViewModel.completedSessionCount.collectAsStateWithLifecycle()
    val chargesToday by homeViewModel.chargesToday.collectAsStateWithLifecycle()
    val dischargeMinutesRemaining by homeViewModel.dischargeMinutesRemaining.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { BatteryHeroCard(snapshot = snapshot, chargesToday = chargesToday) }

        item {
            SpeedAndTimeCard(
                snapshot = snapshot,
                chargingSpeed = chargingSpeed,
                timeEstimate = timeEstimate,
                dischargeMinutesRemaining = dischargeMinutesRemaining
            )
        }

        item { StatsGridCard(snapshot = snapshot) }

        item { BatteryCheckupPromoCard(onOpenCheckup = onNavigateToCheckup) }
        item { LiveMonitorPromoCard(onOpenLiveMonitor = onNavigateToLiveMonitor) }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureShortcutCard(
                    title = "Speed Test",
                    description = "Test your charger\nand cable speed",
                    icon = Icons.Filled.Speed,
                    accentColor = PhosphorGreen,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToSpeedTest
                )
                FeatureShortcutCard(
                    title = "Live Graphs",
                    description = "Real-time graphs\nfor all metrics",
                    icon = Icons.Filled.ShowChart,
                    accentColor = GraphBatteryPct,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToLiveMonitor
                )
            }
        }

        item {
            FeatureShortcutCard(
                title = "Charging Display",
                description = "Animated charging readout on your lock screen — turn on in Settings",
                icon = Icons.Filled.Bolt,
                accentColor = PhosphorGreen,
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToSettings
            )
        }

        item {
            SessionsCard(sessionCount = sessionCount, onViewHistory = onNavigateToHistory)
        }

        item { FooterDisclaimerCard() }
    }
}

// ---------------------------------------------------------------------
// Hero: status + percent + glowing battery illustration
// ---------------------------------------------------------------------

@Composable
private fun BatteryHeroCard(snapshot: BatterySnapshot?, chargesToday: Int) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                StatusBadge(status = snapshot?.chargingStatus ?: ChargingStatus.UNKNOWN)
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${snapshot?.batteryPercent ?: "--"}",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                    )
                }
                Text("Battery Level", style = MaterialTheme.typography.bodyMedium, color = PanelGray)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = PhosphorGreenDim,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Charged $chargesToday time${if (chargesToday == 1) "" else "s"} today",
                        style = MaterialTheme.typography.labelSmall,
                        color = PanelGrayDim
                    )
                }
            }

            BatteryGlowIllustration(
                percent = snapshot?.batteryPercent ?: 0,
                isCharging = snapshot?.isCharging == true
            )
        }
    }
}

@Composable
private fun StatusBadge(status: ChargingStatus) {
    val (label, color) = when (status) {
        ChargingStatus.CHARGING -> "CHARGING" to PhosphorGreen
        ChargingStatus.DISCHARGING -> "DISCHARGING" to VoltageBlue
        ChargingStatus.NOT_CHARGING -> "NOT CHARGING" to PhosphorGreen
        ChargingStatus.FULL -> "FULL" to PhosphorGreen
        ChargingStatus.UNKNOWN -> "UNKNOWN" to PanelGray
    }
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.extraLarge) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

/** A glowing battery jar: concentric radial rings behind a filled battery outline with a bolt icon. */
@Composable
private fun BatteryGlowIllustration(percent: Int, isCharging: Boolean) {
    Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f
            for (ring in 4 downTo 1) {
                drawCircle(
                    color = PhosphorGreen.copy(alpha = 0.06f * ring),
                    radius = maxRadius * (ring / 4f),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // Outline/cap/bolt color must track the theme: pure white is
        // invisible against the near-white light-mode card surface.
        // onSurface resolves to white in dark mode (matching the original
        // look exactly) and to a dark ink color in light mode, so the
        // battery jar stays visible either way.
        val outlineColor = MaterialTheme.colorScheme.onSurface

        Canvas(modifier = Modifier.size(50.dp, 78.dp)) {
            val w = size.width
            val h = size.height
            val capWidth = w * 0.42f
            val capHeight = 6.dp.toPx()
            val bodyTop = capHeight + 3.dp.toPx()

            drawRoundRect(
                color = outlineColor.copy(alpha = 0.85f),
                topLeft = Offset((w - capWidth) / 2f, 0f),
                size = Size(capWidth, capHeight),
                cornerRadius = CornerRadius(2.dp.toPx())
            )

            drawRoundRect(
                color = outlineColor.copy(alpha = 0.9f),
                topLeft = Offset(0f, bodyTop),
                size = Size(w, h - bodyTop),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )

            val fillFraction = (percent / 100f).coerceIn(0f, 1f)
            val inset = 5.dp.toPx()
            val innerHeight = h - bodyTop - inset * 2
            val fillHeight = innerHeight * fillFraction
            if (fillHeight > 0f) {
                drawRoundRect(
                    color = PhosphorGreen,
                    topLeft = Offset(inset, h - inset - fillHeight),
                    size = Size(w - inset * 2, fillHeight),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
            }
        }

        Icon(
            imageVector = Icons.Filled.Bolt,
            contentDescription = null,
            tint = outlineColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ---------------------------------------------------------------------
// Watt / Current / Voltage / Temperature grid
// ---------------------------------------------------------------------

/**
 * Replaces the old flat 4-cell grid with an instrument-cluster layout:
 * one large ring "speedometer" for Watt (the hero reading) with the
 * numeric watt value rendered bigger above it, and three smaller rings
 * underneath for Voltage / Current / Temperature — each visibly smaller
 * than the Watt ring so the hierarchy reads at a glance.
 */
@Composable
private fun StatsGridCard(snapshot: BatterySnapshot?) {
    val powerWatts = snapshot?.let { PowerCalculator.batteryInputPowerWatts(it) }
    val voltage = snapshot?.voltageVolts
    val currentMa = snapshot?.currentMilliAmpsNormalized
    val tempC = snapshot?.temperatureC
    val batteryPercent = snapshot?.batteryPercent
    val chargeCounterMah = (snapshot?.chargeCounterMicroAh as? AvailableOr.Value)
        ?.value?.let { it / 1000.0 }

    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("CHARGING POWER", style = MaterialTheme.typography.labelSmall, color = PanelGrayDim)

            Spacer(modifier = Modifier.height(10.dp))

            // Watt gauge (shrunk from full-width hero size) side-by-side
            // with a Charge Counter gauge of the exact same size — the
            // fraction here is battery% itself (how much of total
            // capacity is currently held), with the real measured mAh
            // reading (BATTERY_PROPERTY_CHARGE_COUNTER) as the center
            // number, so nothing here is a guessed/fabricated max-scale.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactMeterGauge(
                    fraction = if (powerWatts != null) (powerWatts / WATT_RING_MAX_SCALE).toFloat() else 0f,
                    centerValue = powerWatts?.let { "%.1f".format(it) } ?: "—",
                    centerUnit = "WATTS",
                    gaugeColors = listOf(PhosphorGreenDim, PhosphorGreen, PhosphorGreen),
                    size = 128.dp
                )
                CompactMeterGauge(
                    fraction = (batteryPercent ?: 0) / 100f,
                    centerValue = chargeCounterMah?.let { "%.0f".format(it) } ?: "${batteryPercent ?: "—"}",
                    centerUnit = if (chargeCounterMah != null) "mAh" else "%",
                    gaugeColors = listOf(VoltageBlueDim, VoltageBlue, VoltageBlue),
                    size = 128.dp,
                    subLabel = if (chargeCounterMah != null) "${batteryPercent ?: 0}% of capacity" else null
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Three smaller rings, visibly smaller than the gauges above
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStatRing(
                    label = "VOLTAGE",
                    ring = RingMeterData(
                        fraction = (voltage?.toFloat() ?: 0f) / VOLTAGE_RING_MAX_SCALE,
                        value = HomeFormatters.voltsText(voltage),
                        unit = "V"
                    ),
                    color = VoltageBlue
                )
                MiniStatRing(
                    label = "CURRENT",
                    ring = RingMeterData(
                        fraction = (currentMa?.toFloat() ?: 0f) / CURRENT_RING_MAX_SCALE,
                        value = HomeFormatters.mAText(currentMa),
                        unit = "mA"
                    ),
                    color = VoltageBlue
                )
                MiniStatRing(
                    label = "TEMP",
                    ring = RingMeterData(
                        fraction = (tempC?.toFloat() ?: 0f) / TEMPERATURE_RING_MAX_SCALE,
                        value = HomeFormatters.tempText(tempC),
                        unit = "°C"
                    ),
                    color = WarningAmber
                )
            }
        }
    }
}

/** Plain data holder so MiniStatRing doesn't need five separate parameters. */
private data class RingMeterData(val fraction: Float, val value: String, val unit: String)

@Composable
private fun MiniStatRing(label: String, ring: RingMeterData, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RingMeter(
            fraction = ring.fraction.coerceIn(0f, 1f),
            value = ring.value,
            unit = ring.unit,
            color = color,
            size = 84.dp,
            strokeWidth = 7.dp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = PanelGrayDim)
    }
}

// Scale ceilings for each ring's fraction — tuned to typical phone
// charging ranges so the arc sits in a legible mid-to-high position
// during normal charging rather than looking perpetually empty or maxed.
private const val WATT_RING_MAX_SCALE = 30.0
private const val VOLTAGE_RING_MAX_SCALE = 12f
private const val CURRENT_RING_MAX_SCALE = 3000f
private const val TEMPERATURE_RING_MAX_SCALE = 50f

// ---------------------------------------------------------------------
// Charging Speed / Time Remaining
// ---------------------------------------------------------------------

@Composable
private fun SpeedAndTimeCard(
    snapshot: BatterySnapshot?,
    chargingSpeed: ChargingSpeed,
    timeEstimate: ChargeTimeEstimator.TimeEstimate,
    dischargeMinutesRemaining: Long?
) {
    val isCharging = snapshot?.isCharging == true
    val powerWatts = snapshot?.let { PowerCalculator.batteryInputPowerWatts(it) }

    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = PhosphorGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Charging Speed", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isCharging) HomeFormatters.speedLabel(chargingSpeed) else "--",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isCharging) HomeFormatters.wattsText(powerWatts) + " W" else "-- W",
                    style = MaterialTheme.typography.bodySmall,
                    color = PanelGray
                )
                Spacer(modifier = Modifier.height(6.dp))
                MiniTrendLine(
                    values = if (isCharging) listOf(0.2f, 0.5f, 0.35f, 0.7f, 0.55f, 0.8f) else listOf(0.3f, 0.32f, 0.3f, 0.31f, 0.3f),
                    color = PhosphorGreen
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
                    .background(Hairline)
            )

            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = PanelGray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Time Remaining", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = when {
                        isCharging -> HomeFormatters.minutesToReadable(timeEstimate.minutesRemainingToFull)
                        dischargeMinutesRemaining != null -> HomeFormatters.minutesToReadable(dischargeMinutesRemaining)
                        else -> "Calculating…"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        isCharging -> "until full"
                        dischargeMinutesRemaining != null -> "left on battery"
                        else -> "collecting usage data"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = PanelGray
                )
                Spacer(modifier = Modifier.height(6.dp))
                MiniTrendLine(
                    values = if (isCharging) listOf(0.8f, 0.65f, 0.7f, 0.5f, 0.45f, 0.3f) else listOf(0.3f, 0.29f, 0.31f, 0.3f, 0.3f),
                    color = PanelGray
                )
            }
        }
    }
}

@Composable
private fun MiniTrendLine(values: List<Float>, color: Color) {
    SparklineGraph(
        values = values,
        color = color,
        height = 36.dp,
        showMinMax = false
    )
}

// ---------------------------------------------------------------------
// Battery Checkup promo — separate card, directly below the
// watt/voltage/current/temperature grid (StatsGridCard).
// ---------------------------------------------------------------------

@Composable
private fun BatteryCheckupPromoCard(onOpenCheckup: () -> Unit) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(WarningAmber.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Speed, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Battery Checkup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmber
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "A 2-3 minute scan of your battery settings, with a score out of 100 and what to fix",
                    style = MaterialTheme.typography.bodySmall,
                    color = PanelGray
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenCheckup,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = Color.Black)
                ) {
                    Text("Run Checkup", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Promo banner
// ---------------------------------------------------------------------

@Composable
private fun LiveMonitorPromoCard(onOpenLiveMonitor: () -> Unit) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(PhosphorGreen.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = PhosphorGreen, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Monitor Everything in Real-time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PhosphorGreen
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Track wattage, voltage, current, temperature & more with Live Monitor",
                    style = MaterialTheme.typography.bodySmall,
                    color = PanelGray
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenLiveMonitor,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = Color.Black)
                ) {
                    Text("Open Live Monitor", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Speed Test / Live Graphs shortcut cards
// ---------------------------------------------------------------------

@Composable
private fun FeatureShortcutCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    InstrumentCard(modifier = modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accentColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = PanelGray)
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accentColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.IconButton(onClick = onClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// All-time sessions
// ---------------------------------------------------------------------

@Composable
private fun SessionsCard(sessionCount: Int, onViewHistory: () -> Unit) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = PanelGrayDim,
                modifier = Modifier.align(Alignment.TopEnd).size(18.dp)
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("All-time Sessions", style = MaterialTheme.typography.bodyMedium, color = PanelGray)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RingMeter(
                            fraction = (sessionCount / 100f).coerceIn(0f, 1f),
                            value = "$sessionCount",
                            unit = "SESSIONS",
                            color = PhosphorGreen,
                            size = 64.dp,
                            strokeWidth = 6.dp
                        )
                        Column {
                            Text(
                                "$sessionCount Total",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Track your charging history\nand performance",
                                style = MaterialTheme.typography.bodySmall,
                                color = PanelGray
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(onClick = onViewHistory, shape = MaterialTheme.shapes.extraLarge) {
                    Text("View History", color = PhosphorGreen, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = PhosphorGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Footer disclaimer
// ---------------------------------------------------------------------

@Composable
private fun FooterDisclaimerCard() {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(PhosphorGreen.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = PhosphorGreen, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = PowerTerminology.WATTAGE_ESTIMATE_DISCLAIMER,
                    style = MaterialTheme.typography.bodySmall,
                    color = PanelGray
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                Icons.Filled.ElectricalServices,
                contentDescription = null,
                tint = PanelGrayDim,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
