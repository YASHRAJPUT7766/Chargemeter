package com.yash.chargemeterpro.ui.screens.livemonitor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.yash.chargemeterpro.domain.model.AvailableOr
import com.yash.chargemeterpro.domain.usecase.PowerTerminology
import com.yash.chargemeterpro.ui.components.DisclaimerText
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.components.ReadingRow
import com.yash.chargemeterpro.ui.components.RingMeter
import com.yash.chargemeterpro.ui.components.StatusBadgeRow
import com.yash.chargemeterpro.ui.theme.GraphBatteryPct
import com.yash.chargemeterpro.ui.theme.GraphCurrent
import com.yash.chargemeterpro.ui.theme.GraphTemperature
import com.yash.chargemeterpro.ui.theme.GraphVoltage
import com.yash.chargemeterpro.ui.theme.GraphWattage
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PhosphorGreen

/**
 * Live Monitor redesign: the old line-chart-per-metric graph is gone —
 * ChargeFlow's spec explicitly rules out traditional line charts
 * anywhere in the app. In its place: a focused hero ring for whichever
 * metric is selected (reusing the same metric-switcher chips the graph
 * used to sit under) plus a small trend arrow computed from the same
 * rolling sample buffer the old graph plotted, so "is this climbing or
 * settling" is still visible at a glance — just as an indicator, not a
 * plotted line. Everything below (Live Readings, Charger Analysis) is
 * now rings/badges instead of plain "label ... value" rows.
 */
@Composable
fun LiveMonitorScreen(viewModel: LiveMonitorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            MetricSelector(selected = state.selectedMetric, onSelect = viewModel::selectMetric)
        }

        item {
            HeroMetricCard(state = state)
        }

        item {
            LiveReadingsCard(state = state)
        }

        item {
            ChargerAnalysisCard(state = state)
        }

        item {
            DisclaimerText(text = PowerTerminology.WATTAGE_ESTIMATE_DISCLAIMER)
        }
    }
}

@Composable
private fun MetricSelector(selected: GraphMetric, onSelect: (GraphMetric) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GraphMetric.entries.forEach { metric ->
            FilterChip(
                selected = selected == metric,
                onClick = { onSelect(metric) },
                label = { Text(metricLabel(metric)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = metricColor(metric).copy(alpha = 0.18f),
                    selectedLabelColor = metricColor(metric)
                )
            )
        }
    }
}

private fun metricLabel(metric: GraphMetric): String = when (metric) {
    GraphMetric.WATTAGE -> "Wattage"
    GraphMetric.CURRENT -> "Current"
    GraphMetric.VOLTAGE -> "Voltage"
    GraphMetric.BATTERY_PERCENT -> "Battery %"
    GraphMetric.TEMPERATURE -> "Temperature"
}

private fun metricUnit(metric: GraphMetric): String = when (metric) {
    GraphMetric.WATTAGE -> "W"
    GraphMetric.CURRENT -> "mA"
    GraphMetric.VOLTAGE -> "V"
    GraphMetric.BATTERY_PERCENT -> "%"
    GraphMetric.TEMPERATURE -> "°C"
}

/** What "full scale" (fraction = 1.0) means on the hero ring, per metric — typical-range references, not hard device maximums, chosen so ordinary readings still show a legible, non-maxed-out arc. */
private fun metricRingScale(metric: GraphMetric): Float = when (metric) {
    GraphMetric.WATTAGE -> 30f
    GraphMetric.CURRENT -> 3000f
    GraphMetric.VOLTAGE -> 5f
    GraphMetric.BATTERY_PERCENT -> 100f
    GraphMetric.TEMPERATURE -> 45f
}

@Composable
private fun metricColor(metric: GraphMetric) = when (metric) {
    GraphMetric.WATTAGE -> GraphWattage
    GraphMetric.CURRENT -> GraphCurrent
    GraphMetric.VOLTAGE -> GraphVoltage
    GraphMetric.BATTERY_PERCENT -> GraphBatteryPct
    GraphMetric.TEMPERATURE -> GraphTemperature
}

/**
 * The hero visual for whichever metric is selected: a large ring (the
 * live value) plus a trend arrow derived from comparing the latest
 * sample against one from a few seconds earlier in the same rolling
 * buffer the old graph used — up, down, or steady. This is intentionally
 * NOT a plotted line; it's a single glanceable direction indicator.
 */
@Composable
private fun HeroMetricCard(state: LiveMonitorUiState) {
    val points = state.graphPoints[state.selectedMetric].orEmpty()
    val latestValue = points.lastOrNull()?.value
    val trendReference = if (points.size >= 6) points[points.size - 6].value else points.firstOrNull()?.value

    val trend: Trend = when {
        latestValue == null || trendReference == null -> Trend.STEADY
        latestValue > trendReference * 1.03f -> Trend.UP
        latestValue < trendReference * 0.97f -> Trend.DOWN
        else -> Trend.STEADY
    }

    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    metricLabel(state.selectedMetric),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TrendChip(trend = trend, color = metricColor(state.selectedMetric))
            }
            Spacer(modifier = Modifier.height(12.dp))
            RingMeter(
                fraction = (latestValue ?: 0f) / metricRingScale(state.selectedMetric),
                value = latestValue?.let { formatMetricValue(state.selectedMetric, it) } ?: "—",
                unit = metricUnit(state.selectedMetric),
                color = metricColor(state.selectedMetric),
                size = 168.dp,
                strokeWidth = 14.dp
            )
        }
    }
}

private enum class Trend { UP, DOWN, STEADY }

@Composable
private fun TrendChip(trend: Trend, color: androidx.compose.ui.graphics.Color) {
    val (icon, description) = when (trend) {
        Trend.UP -> Icons.Filled.ArrowUpward to "Rising"
        Trend.DOWN -> Icons.Filled.ArrowDownward to "Falling"
        Trend.STEADY -> Icons.Filled.Remove to "Steady"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = description, tint = color, modifier = Modifier.height(16.dp))
        Text(description, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

private fun formatMetricValue(metric: GraphMetric, value: Float): String = when (metric) {
    GraphMetric.WATTAGE -> "%.1f".format(value)
    GraphMetric.CURRENT -> "%.0f".format(value)
    GraphMetric.VOLTAGE -> "%.2f".format(value)
    GraphMetric.BATTERY_PERCENT -> "%.0f".format(value)
    GraphMetric.TEMPERATURE -> "%.1f".format(value)
}

@Composable
private fun LiveReadingsCard(state: LiveMonitorUiState) {
    val snapshot = state.snapshot
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Live Readings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RingMeter(
                    fraction = ((snapshot?.voltageVolts ?: 0.0) / 5.0).toFloat(),
                    value = snapshot?.voltageVolts?.let { "%.2f".format(it) } ?: "—",
                    unit = "V",
                    color = GraphVoltage,
                    size = 76.dp,
                    strokeWidth = 7.dp
                )
                RingMeter(
                    fraction = ((snapshot?.currentMilliAmpsNormalized ?: 0.0) / 3000.0).toFloat(),
                    value = snapshot?.currentMilliAmpsNormalized?.let { "%.0f".format(it) } ?: "—",
                    unit = "mA",
                    color = GraphCurrent,
                    size = 76.dp,
                    strokeWidth = 7.dp
                )
                RingMeter(
                    fraction = ((snapshot?.temperatureC ?: 0.0) / 45.0).toFloat(),
                    value = snapshot?.temperatureC?.let { "%.0f".format(it) } ?: "—",
                    unit = "°C",
                    color = GraphTemperature,
                    size = 76.dp,
                    strokeWidth = 7.dp
                )
            }

            StatusBadgeRow(
                label = "Status",
                value = snapshot?.chargingStatus?.name,
                accentColor = PhosphorGreen
            )
            StatusBadgeRow(
                label = "Health",
                value = snapshot?.health?.name,
                accentColor = GraphVoltage
            )
            StatusBadgeRow(
                label = "Technology",
                value = snapshot?.technology?.orNull(),
                accentColor = PanelGray
            )
            ReadingRow(
                label = "Battery Input Power",
                value = snapshot?.let { com.yash.chargemeterpro.domain.usecase.PowerCalculator.batteryInputPowerWatts(it) }
                    ?.let { "%.2f".format(it) },
                unit = "W"
            )
            ReadingRow(
                label = "Capacity (charge counter)",
                value = (snapshot?.chargeCounterMicroAh as? AvailableOr.Value)?.value?.let { "%.0f".format(it / 1000.0) },
                unit = "mAh"
            )
            ReadingRow(label = "Cycle Count", value = snapshot?.cycleCount?.orNull()?.toString())
        }
    }
}

@Composable
private fun ChargerAnalysisCard(state: LiveMonitorUiState) {
    val analysis = state.chargerAnalysis
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Charger Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            StatusBadgeRow(label = "Plug Type", value = analysis?.plugType?.name, accentColor = PhosphorGreen)
            StatusBadgeRow(
                label = "USB Connected",
                value = analysis?.usbConnected?.let { if (it) "Yes" else "No" },
                accentColor = if (analysis?.usbConnected == true) PhosphorGreen else PanelGray
            )
            StatusBadgeRow(
                label = "USB Device Enumerated",
                value = analysis?.usbDeviceDescriptorPresent?.let { if (it) "Yes" else "No" },
                accentColor = if (analysis?.usbDeviceDescriptorPresent == true) PhosphorGreen else PanelGray
            )
            StatusBadgeRow(
                label = "Fast Charge Indicated",
                value = analysis?.fastChargeIndicated?.let { if (it) "Yes" else "No" },
                accentColor = if (analysis?.fastChargeIndicated == true) PhosphorGreen else PanelGray
            )
            ReadingRow(label = "Device Max Input Report", value = analysis?.deviceReportedMaxInputDetail?.orNull())
            ReadingRow(label = "USB-PD Status", value = analysis?.usbPdStatus?.orNull())
            Spacer(modifier = Modifier.height(4.dp))
            DisclaimerText(text = PowerTerminology.WALL_OUTPUT_UNAVAILABLE_DISCLAIMER)
        }
    }
}
