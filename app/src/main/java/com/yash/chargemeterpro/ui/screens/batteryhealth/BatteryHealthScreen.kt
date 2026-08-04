package com.yash.chargemeterpro.ui.screens.batteryhealth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/**
 * Top hero card: big "100%  Excellent" reading + capacity-fade box on the
 * left, a glowing bolt-in-ring icon on the right — matches the reference
 * layout the user asked to match (score-as-percent, qualitative label,
 * capacity fade called out separately, no numeric "based on" chip list).
 */
@Composable
private fun HealthScoreCard(result: BatteryHealthScorer.HealthScoreResult?) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        when (result) {
            is BatteryHealthScorer.HealthScoreResult.Score -> {
                val color = scoreColor(result.value)
                val label = scoreLabel(result.value)
                val fadeText = result.basedOn
                    .firstOrNull { it.startsWith("Estimated capacity fade") }
                    ?.substringAfter(": ")
                    ?: "—"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Battery Health",
                            style = MaterialTheme.typography.labelLarge,
                            color = PanelGray
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${result.value}",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                            Text(
                                "%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = color,
                                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                            )
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = color
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Column(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                "Estimated capacity fade",
                                style = MaterialTheme.typography.labelSmall,
                                color = PanelGray
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                fadeText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PhosphorGreen
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    GlowingBoltRing(color = color)
                }
                Spacer(modifier = Modifier.height(4.dp))
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

/** Concentric glow-ring with a bolt glyph in the center — the "Excellent" badge icon in the reference image. */
@Composable
private fun GlowingBoltRing(color: Color) {
    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.10f))
        )
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(CircleShape)
                .background(Color.Transparent)
                .border(3.dp, color, CircleShape)
        )
        Icon(
            imageVector = Icons.Filled.Bolt,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(34.dp)
        )
    }
}

private fun scoreColor(score: Int) = when {
    score >= 80 -> PhosphorGreen
    score >= 60 -> VoltageBlue
    score >= 40 -> WarningAmber
    else -> CriticalRed
}

private fun scoreLabel(score: Int) = when {
    score >= 90 -> "Excellent"
    score >= 75 -> "Good"
    score >= 50 -> "Fair"
    else -> "Poor"
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
                fraction = (((snapshot?.chargeCounterMicroAh as? AvailableOr.Value)?.value?.toDouble() ?: 0.0) / 5_000_000.0).toFloat(),
                color = PhosphorGreen
            )
        }
    }
}
