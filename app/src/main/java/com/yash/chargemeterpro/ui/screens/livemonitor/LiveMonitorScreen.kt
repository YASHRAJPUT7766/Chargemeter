package com.yash.chargemeterpro.ui.screens.livemonitor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.chargemeterpro.domain.model.AvailableOr
import com.yash.chargemeterpro.domain.usecase.PowerTerminology
import com.yash.chargemeterpro.ui.components.DisclaimerText
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.components.LiveLineChart
import com.yash.chargemeterpro.ui.components.ReadingRow
import com.yash.chargemeterpro.ui.theme.GraphBatteryPct
import com.yash.chargemeterpro.ui.theme.GraphCurrent
import com.yash.chargemeterpro.ui.theme.GraphTemperature
import com.yash.chargemeterpro.ui.theme.GraphVoltage
import com.yash.chargemeterpro.ui.theme.GraphWattage
import com.yash.chargemeterpro.ui.theme.PanelGray

@Composable
fun LiveMonitorScreen(viewModel: LiveMonitorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Live Monitor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        item {
            MetricSelector(selected = state.selectedMetric, onSelect = viewModel::selectMetric)
        }

        item {
            GraphCard(state = state)
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

@Composable
private fun metricColor(metric: GraphMetric) = when (metric) {
    GraphMetric.WATTAGE -> GraphWattage
    GraphMetric.CURRENT -> GraphCurrent
    GraphMetric.VOLTAGE -> GraphVoltage
    GraphMetric.BATTERY_PERCENT -> GraphBatteryPct
    GraphMetric.TEMPERATURE -> GraphTemperature
}

@Composable
private fun GraphCard(state: LiveMonitorUiState) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${metricLabel(state.selectedMetric)} vs Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(metricUnit(state.selectedMetric), style = MaterialTheme.typography.bodySmall, color = PanelGray)
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
            LiveLineChart(
                points = state.graphPoints[state.selectedMetric].orEmpty(),
                lineColor = metricColor(state.selectedMetric),
                unitSuffix = metricUnit(state.selectedMetric)
            )
        }
    }
}

@Composable
private fun LiveReadingsCard(state: LiveMonitorUiState) {
    val snapshot = state.snapshot
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Live Readings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ReadingRow(label = "Status", value = snapshot?.chargingStatus?.name)
            ReadingRow(label = "Battery Level", value = snapshot?.batteryPercent?.toString(), unit = "%")
            ReadingRow(label = "Voltage", value = snapshot?.voltageVolts?.let { "%.3f".format(it) }, unit = "V")
            ReadingRow(
                label = "Current",
                value = snapshot?.currentMilliAmpsNormalized?.let { "%.1f".format(it) },
                unit = "mA"
            )
            ReadingRow(
                label = "Battery Input Power",
                value = snapshot?.let { com.yash.chargemeterpro.domain.usecase.PowerCalculator.batteryInputPowerWatts(it) }
                    ?.let { "%.2f".format(it) },
                unit = "W"
            )
            ReadingRow(label = "Temperature", value = snapshot?.temperatureC?.let { "%.1f".format(it) }, unit = "°C")
            ReadingRow(label = "Health", value = snapshot?.health?.name)
            ReadingRow(label = "Technology", value = snapshot?.technology?.orNull())
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Charger Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ReadingRow(label = "Plug Type", value = analysis?.plugType?.name)
            ReadingRow(label = "USB Connected", value = analysis?.usbConnected?.let { if (it) "Yes" else "No" })
            ReadingRow(
                label = "USB Device Enumerated",
                value = analysis?.usbDeviceDescriptorPresent?.let { if (it) "Yes" else "No" }
            )
            ReadingRow(
                label = "Fast Charge Indicated",
                value = analysis?.fastChargeIndicated?.let { if (it) "Yes" else "No" }
            )
            ReadingRow(label = "Device Max Input Report", value = analysis?.deviceReportedMaxInputDetail?.orNull())
            ReadingRow(label = "USB-PD Status", value = analysis?.usbPdStatus?.orNull())
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
            DisclaimerText(text = PowerTerminology.WALL_OUTPUT_UNAVAILABLE_DISCLAIMER)
        }
    }
}
