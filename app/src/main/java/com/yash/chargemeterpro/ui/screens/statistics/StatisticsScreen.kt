package com.yash.chargemeterpro.ui.screens.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.chargemeterpro.domain.usecase.PowerTerminology
import com.yash.chargemeterpro.ui.components.CapsuleMeterRow
import com.yash.chargemeterpro.ui.components.DisclaimerText
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.components.RingMeter
import com.yash.chargemeterpro.ui.components.SparklineGraph
import com.yash.chargemeterpro.ui.theme.GraphTemperature
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PhosphorGreen
import com.yash.chargemeterpro.ui.theme.VoltageBlue
import com.yash.chargemeterpro.ui.theme.WarningAmber

/**
 * Stats page redesign: every number here is expressed through a ring
 * meter, capsule bar, or a big color-coded figure inside a small card —
 * never a bare "Label ......... value" text row and never a line chart,
 * per the ChargeFlow spec. Scales (what fraction=1.0 means for each
 * ring) are chosen to be typical-range references, not hard maximums —
 * e.g. the power ring reads "full" around 30W, a fast-charge ceiling for
 * most phones, so a normal 10-20W session still shows a satisfying,
 * legible arc instead of looking nearly empty against an unrealistic
 * upper bound.
 */
@Composable
fun StatisticsScreen(
    onNavigateToBatteryHealth: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { TodayCard(state) }
        item { AllTimeCard(state) }
        item { DrainRateCard(state) }
        item { BatteryHealthEntryCard(onClick = onNavigateToBatteryHealth) }
        item { HistoryEntryCard(onClick = onNavigateToHistory) }
        item { DisclaimerText(text = PowerTerminology.WATTAGE_ESTIMATE_DISCLAIMER) }
    }
}

/**
 * History shortcut on Stats (spec item #5: "add a History shortcut on
 * relevant pages such as the Stats page"), matching the visual style of
 * BatteryHealthEntryCard right above it.
 */
@Composable
private fun HistoryEntryCard(onClick: () -> Unit) {
    InstrumentCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.History, contentDescription = null, tint = VoltageBlue)
                Column {
                    Text("Charging History", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("View all past charging sessions", style = MaterialTheme.typography.labelSmall, color = PanelGray)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = PanelGray)
        }
    }
}

@Composable
private fun BatteryHealthEntryCard(onClick: () -> Unit) {
    InstrumentCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = PhosphorGreen)
                Column {
                    Text("Battery Health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Estimated capacity, health score & diagnostics",
                        style = MaterialTheme.typography.bodySmall,
                        color = PanelGray
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = PanelGray)
        }
    }
}

@Composable
private fun TodayCard(state: StatisticsUiState) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = PhosphorGreen, modifier = Modifier.height(18.dp))
                Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sessions today, scaled against a "busy day" reference of 4 so
                // a typical 1-2 session day still reads clearly on the ring.
                RingMeter(
                    fraction = (state.todaySessionCount / 4f),
                    value = state.todaySessionCount.toString(),
                    unit = "SESSIONS",
                    color = PhosphorGreen
                )
                RingMeter(
                    fraction = (state.todayTotalChargingMinutes / 180f), // 3h reference
                    value = formatMinutesCompact(state.todayTotalChargingMinutes),
                    unit = "CHARGE TIME",
                    color = VoltageBlue
                )
                RingMeter(
                    fraction = ((state.todayAveragePowerWatts ?: 0.0) / 30.0).toFloat(),
                    value = state.todayAveragePowerWatts?.let { "%.0f".format(it) } ?: "—",
                    unit = "AVG WATTS",
                    color = WarningAmber
                )
            }
        }
    }
}

@Composable
private fun AllTimeCard(state: StatisticsUiState) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("All Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            CapsuleMeterRow(
                label = "Total Sessions",
                valueText = state.allTimeSessionCount.toString(),
                fraction = (state.allTimeSessionCount / 100f), // 100 sessions reference ceiling
                color = PhosphorGreen
            )
            CapsuleMeterRow(
                label = "Average Charging Power",
                valueText = state.allTimeAveragePowerWatts?.let { "%.1f W".format(it) } ?: "—",
                fraction = ((state.allTimeAveragePowerWatts ?: 0.0) / 30.0).toFloat(),
                color = WarningAmber
            )
            CapsuleMeterRow(
                label = "Maximum Charging Power",
                valueText = state.allTimeMaxPowerWatts?.let { "%.1f W".format(it) } ?: "—",
                fraction = ((state.allTimeMaxPowerWatts ?: 0.0) / 65.0).toFloat(), // 65W = common fast-charger ceiling
                color = VoltageBlue
            )
            CapsuleMeterRow(
                label = "Total Energy Delivered",
                valueText = state.allTimeTotalEnergyWh?.let { "%.0f Wh".format(it) } ?: "—",
                fraction = ((state.allTimeTotalEnergyWh ?: 0.0) / 500.0).toFloat(), // 500Wh reference ceiling
                color = PhosphorGreen
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            CapsuleMeterRow(
                label = "Average Session Duration",
                valueText = state.averageSessionDurationMinutes?.let { formatMinutesCompact(it) } ?: "—",
                fraction = ((state.averageSessionDurationMinutes ?: 0L) / 180f), // 3h reference ceiling
                color = PhosphorGreen
            )
        }
    }
}

@Composable
private fun DrainRateCard(state: StatisticsUiState) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RingMeter(
                    // 3%/hour is a rough "high drain" reference — a healthy idle
                    // drain is usually well under 1.5%/hour, so this scale keeps
                    // normal readings from looking maxed-out.
                    fraction = ((state.drainRate.percentPerHour ?: 0.0) / 3.0).toFloat(),
                    value = state.drainRate.percentPerHour?.let { "%.1f".format(it) } ?: "—",
                    unit = "%/HOUR",
                    color = GraphTemperature,
                    size = 76.dp,
                    strokeWidth = 7.dp
                )
                Column {
                    Text(
                        "Battery Drain (7 days)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Based on ${state.drainRate.sampleCount} background samples collected while not charging, taken roughly every 15 minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PanelGray
                    )
                }
            }

            if (state.batteryPercentHistory.size >= 2) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                SparklineGraph(
                    values = state.batteryPercentHistory,
                    color = GraphTemperature,
                    label = "Battery % (last 7 days)",
                    valueSuffix = "%",
                    height = 100.dp
                )
            }
        }
    }
}

private fun formatMinutesCompact(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h${m}m" else "${m}m"
}
