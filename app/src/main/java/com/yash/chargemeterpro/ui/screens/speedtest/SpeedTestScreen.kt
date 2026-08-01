package com.yash.chargemeterpro.ui.screens.speedtest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.chargemeterpro.domain.usecase.PowerTerminology
import com.yash.chargemeterpro.ui.components.DisclaimerText
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.components.ReadingRow
import com.yash.chargemeterpro.ui.components.WattMeterGauge
import com.yash.chargemeterpro.ui.navigation.ScreenBackTopBar
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PhosphorGreen

@Composable
fun SpeedTestScreen(onBack: () -> Unit, viewModel: SpeedTestViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenBackTopBar(title = "Charging Speed Test", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (state.phase) {
                SpeedTestPhase.IDLE -> item { IdleContent(state, viewModel) }
                SpeedTestPhase.RUNNING -> item { RunningContent(state, viewModel) }
                SpeedTestPhase.COMPLETED -> item { ReportContent(state, viewModel) }
            }
        }
    }
}

@Composable
private fun IdleContent(state: SpeedTestUiState, viewModel: SpeedTestViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InstrumentCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Select Duration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60L to "1 min", 300L to "5 min", 600L to "10 min").forEach { (secs, label) ->
                        FilterChip(
                            selected = state.selectedDurationSeconds == secs,
                            onClick = { viewModel.selectDuration(secs) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                CustomDurationField(
                    selectedSeconds = state.selectedDurationSeconds,
                    onCustomSeconds = viewModel::selectDuration
                )
            }
        }

        if (state.notCurrentlyCharging) {
            InstrumentCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Plug in your device to start a speed test — the test measures charging power, so it can't run while unplugged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PanelGray
                )
            }
        }

        Button(
            onClick = viewModel::startTest,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Start Test")
        }

        DisclaimerText(text = PowerTerminology.WATTAGE_ESTIMATE_DISCLAIMER)
    }
}

@Composable
private fun CustomDurationField(selectedSeconds: Long, onCustomSeconds: (Long) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() } },
            label = { Text("Custom (minutes)") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedButton(onClick = {
            text.toLongOrNull()?.let { minutes -> if (minutes > 0) onCustomSeconds(minutes * 60) }
        }) { Text("Set") }
    }
}

@Composable
private fun RunningContent(state: SpeedTestUiState, viewModel: SpeedTestViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        InstrumentCard(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Test in progress — ${formatSeconds(state.elapsedSeconds)} / ${formatSeconds(state.selectedDurationSeconds)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { (state.elapsedSeconds.toFloat() / state.selectedDurationSeconds.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = PhosphorGreen
                )
                Spacer(modifier = Modifier.height(24.dp))
                WattMeterGauge(
                    currentWatts = state.currentPowerWatts,
                    maxScaleWatts = 30.0,
                    modifier = Modifier.fillMaxWidth(0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                ReadingRow(
                    label = "Battery Percentage",
                    value = state.liveSamples.lastOrNull()?.batteryPercent?.toString(),
                    unit = "%"
                )
                ReadingRow(
                    label = "Samples Recorded",
                    value = state.liveSamples.size.toString()
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = viewModel::cancelTest, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel Test")
        }
    }
}

@Composable
private fun ReportContent(state: SpeedTestUiState, viewModel: SpeedTestViewModel) {
    val report = state.report ?: return
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InstrumentCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Test Report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))
                ReadingRow(label = "Duration", value = formatSeconds(report.durationSeconds))
                ReadingRow(label = "Battery Gained", value = "+${report.percentGained}", unit = "%")
                ReadingRow(label = "Started at", value = "${report.startBatteryPercent}", unit = "%")
                ReadingRow(label = "Ended at", value = "${report.endBatteryPercent}", unit = "%")
                ReadingRow(
                    label = "Average Power",
                    value = report.averagePowerWatts?.let { "%.2f".format(it) },
                    unit = "W"
                )
                ReadingRow(
                    label = "Maximum Power",
                    value = report.maxPowerWatts?.let { "%.2f".format(it) },
                    unit = "W"
                )
                ReadingRow(label = "Samples Collected", value = report.samples.size.toString())
            }
        }

        DisclaimerText(text = PowerTerminology.WATTAGE_ESTIMATE_DISCLAIMER)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::resetTest, modifier = Modifier.weight(1f)) {
                Text("Run Another Test")
            }
        }
    }
}

private fun formatSeconds(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
