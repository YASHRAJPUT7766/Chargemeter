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
import androidx.compose.foundation.layout.padding
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
import com.yash.chargemeterpro.ui.components.CapsuleMeterRow
import com.yash.chargemeterpro.ui.components.DisclaimerText
import com.yash.chargemeterpro.ui.components.InstrumentCard
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        result.basedOn.forEach { line ->
                            androidx.compose.material3.Surface(
                                color = scoreColor(result.value).copy(alpha = 0.14f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    line,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scoreColor(result.value),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
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
                    fraction = (((state.deviceSkinTempC as? AvailableOr.Value)?.value ?: 0f) / 45f),
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

            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RingMeter(
                    fraction = ((snapshot?.voltageVolts ?: 0.0) / 5.0).toFloat(),
                    value = snapshot?.voltageVolts?.let { "%.2f".format(it) } ?: "—",
                    unit = "VOLTS",
                    color = VoltageBlue,
                    size = 76.dp,
                    strokeWidth = 7.dp
                )
                RingMeter(
                    fraction = ((snapshot?.currentMilliAmpsNormalized ?: 0.0) / 3000.0).toFloat(),
                    value = snapshot?.currentMilliAmpsNormalized?.let { "%.0f".format(it) } ?: "—",
                    unit = "mA",
                    color = WarningAmber,
                    size = 76.dp,
                    strokeWidth = 7.dp
                )
                RingMeter(
                    fraction = ((snapshot?.cycleCount?.orNull()?.toFloat() ?: 0f) / 1000f),
                    value = snapshot?.cycleCount?.orNull()?.toString() ?: "—",
                    unit = "CYCLES",
                    color = PanelGray,
                    size = 76.dp,
                    strokeWidth = 7.dp
                )
            }

            CapsuleMeterRow(
                label = "Charge Counter",
                valueText = (snapshot?.chargeCounterMicroAh as? AvailableOr.Value)?.value?.let { "%.0f mAh".format(it / 1000.0) } ?: "—",
                fraction = (((snapshot?.chargeCounterMicroAh as? AvailableOr.Value)?.value ?: 0.0) / 5_000_000.0).toFloat(),
                color = PhosphorGreen
            )
        }
    }
}
