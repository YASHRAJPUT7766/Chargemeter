package com.yash.chargemeterpro.ui.screens.batteryhealth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
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
import com.yash.chargemeterpro.domain.usecase.BatteryHealthScorer
import com.yash.chargemeterpro.domain.usecase.PowerTerminology
import com.yash.chargemeterpro.ui.components.DisclaimerText
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.components.ReadingRow
import com.yash.chargemeterpro.ui.components.RingMeter
import com.yash.chargemeterpro.ui.components.StatusBadgeRow
import com.yash.chargemeterpro.ui.theme.CriticalRed
import com.yash.chargemeterpro.ui.theme.GraphTemperature
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PhosphorGreen
import com.yash.chargemeterpro.ui.theme.VoltageBlue
import com.yash.chargemeterpro.ui.theme.WarningAmber

@Composable
fun BatteryHealthScreen(viewModel: BatteryHealthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HealthScoreCard(result = state.healthScoreResult)
        }

        item {
            DiagnosticsCard(state = state)
        }

        item {
            DisclaimerText(text = PowerTerminology.HEALTH_SCORE_DISCLAIMER)
        }
    }
}

@Composable
private fun HealthScoreCard(result: BatteryHealthScorer.HealthScoreResult?) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        when (result) {
            is BatteryHealthScorer.HealthScoreResult.Score -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Battery Health Score",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { result.value / 100f },
                            modifier = Modifier.size(140.dp),
                            strokeWidth = 10.dp,
                            color = scoreColor(result.value),
                            trackColor = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "${result.value}",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor(result.value)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Based on:",
                        style = MaterialTheme.typography.bodySmall,
                        color = PanelGray
                    )
                    result.basedOn.forEach { line ->
                        Text("• $line", style = MaterialTheme.typography.bodySmall, color = PanelGray)
                    }
                }
            }
            is BatteryHealthScorer.HealthScoreResult.InsufficientData -> {
                Column {
                    Text(
                        "Health Score Not Available",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(result.reason, style = MaterialTheme.typography.bodyMedium, color = PanelGray)
                }
            }
            null -> {
                Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = PanelGray)
            }
        }
    }
}

@Composable
private fun scoreColor(score: Int) = when {
    score >= 80 -> PhosphorGreen
    score >= 60 -> VoltageBlue
    score >= 40 -> WarningAmber
    else -> CriticalRed
}

@Composable
private fun DiagnosticsCard(state: BatteryHealthUiState) {
    val snapshot = state.snapshot
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RingMeter(
                    fraction = (snapshot?.batteryPercent ?: 0) / 100f,
                    value = snapshot?.batteryPercent?.toString() ?: "—",
                    unit = "BATTERY",
                    color = PhosphorGreen
                )
                RingMeter(
                    fraction = ((snapshot?.temperatureC ?: 0.0) / 45.0).toFloat(),
                    value = snapshot?.temperatureC?.let { "%.0f".format(it) } ?: "—",
                    unit = "BATT °C",
                    color = GraphTemperature
                )
                RingMeter(
                    fraction = (((state.deviceSkinTempC as? AvailableOr.Value)?.value ?: 0.0) / 45.0).toFloat(),
                    value = (state.deviceSkinTempC as? AvailableOr.Value)?.value?.let { "%.0f".format(it) } ?: "—",
                    unit = "SKIN °C",
                    color = WarningAmber
                )
            }

            StatusBadgeRow(label = "Charging Status", value = snapshot?.chargingStatus?.name, accentColor = PhosphorGreen)
            StatusBadgeRow(label = "Health Status", value = snapshot?.health?.name, accentColor = VoltageBlue)
            StatusBadgeRow(label = "Technology", value = snapshot?.technology?.orNull(), accentColor = PanelGray)
            StatusBadgeRow(
                label = "Charging Policy",
                value = (snapshot?.chargingPolicy as? AvailableOr.Value)?.value?.name,
                accentColor = PanelGray
            )

            ReadingRow(label = "Voltage", value = snapshot?.voltageVolts?.let { "%.3f".format(it) }, unit = "V")
            ReadingRow(
                label = "Current",
                value = snapshot?.currentMilliAmpsNormalized?.let { "%.1f".format(it) },
                unit = "mA"
            )
            ReadingRow(
                label = "Charge Counter",
                value = (snapshot?.chargeCounterMicroAh as? AvailableOr.Value)?.value?.let { "%.0f".format(it / 1000.0) },
                unit = "mAh"
            )
            ReadingRow(label = "Cycle Count", value = snapshot?.cycleCount?.orNull()?.toString())
        }
    }
}
