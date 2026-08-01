package com.yash.chargemeterpro.ui.screens.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.chargemeterpro.domain.usecase.PowerTerminology
import com.yash.chargemeterpro.ui.components.DisclaimerText
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.components.ReadingRow
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PhosphorGreen

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Statistics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        item { TodaySummaryCard(state) }
        item { AllTimeSummaryCard(state) }
        item { DrainRateCard(state) }
        item { DisclaimerText(text = PowerTerminology.WATTAGE_ESTIMATE_DISCLAIMER) }
    }
}

@Composable
private fun TodaySummaryCard(state: StatisticsUiState) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBlock(
                    label = "Sessions",
                    value = state.todaySessionCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatBlock(
                    label = "Charging Time",
                    value = formatMinutes(state.todayTotalChargingMinutes),
                    modifier = Modifier.weight(1f)
                )
            }
            ReadingRow(
                label = "Average Power Today",
                value = state.todayAveragePowerWatts?.let { "%.1f".format(it) },
                unit = "W"
            )
        }
    }
}

@Composable
private fun AllTimeSummaryCard(state: StatisticsUiState) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("All Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ReadingRow(label = "Total Sessions", value = state.allTimeSessionCount.toString())
            ReadingRow(
                label = "Average Charging Power",
                value = state.allTimeAveragePowerWatts?.let { "%.2f".format(it) },
                unit = "W"
            )
            ReadingRow(
                label = "Maximum Charging Power",
                value = state.allTimeMaxPowerWatts?.let { "%.2f".format(it) },
                unit = "W"
            )
            ReadingRow(
                label = "Total Estimated Energy",
                value = state.allTimeTotalEnergyWh?.let { "%.1f".format(it) },
                unit = "Wh"
            )
            ReadingRow(
                label = "Average Session Duration",
                value = state.averageSessionDurationMinutes?.let { formatMinutes(it) }
            )
        }
    }
}

@Composable
private fun DrainRateCard(state: StatisticsUiState) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Battery Drain (last 7 days)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            ReadingRow(
                label = "Estimated Drain Rate",
                value = state.drainRate.percentPerHour?.let { "%.1f".format(it) },
                unit = "%/hour"
            )
            Text(
                "Based on ${state.drainRate.sampleCount} background samples collected while the device wasn't charging. Samples are taken roughly every 15 minutes to conserve battery.",
                style = MaterialTheme.typography.bodySmall,
                color = PanelGray
            )
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = PanelGray)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = PhosphorGreen)
    }
}

private fun formatMinutes(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
