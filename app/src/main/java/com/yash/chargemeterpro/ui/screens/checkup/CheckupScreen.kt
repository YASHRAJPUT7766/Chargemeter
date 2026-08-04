package com.yash.chargemeterpro.ui.screens.checkup

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yash.chargemeterpro.domain.usecase.BatteryCheckupScorer
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.components.RingMeter
import com.yash.chargemeterpro.ui.theme.CriticalRed
import com.yash.chargemeterpro.ui.theme.Hairline
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PanelGrayDim
import com.yash.chargemeterpro.ui.theme.PhosphorGreen
import com.yash.chargemeterpro.ui.theme.WarningAmber

@Composable
fun CheckupScreen(
    onBack: () -> Unit,
    viewModel: CheckupViewModel = hiltViewModel()
) {
    val stage by viewModel.stage.collectAsState()

    LaunchedEffect(Unit) {
        if (stage is CheckupStage.Idle) viewModel.startScan()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = stage) {
            is CheckupStage.Idle -> Unit
            is CheckupStage.Scanning -> ScanningContent(current)
            is CheckupStage.Done -> ResultContent(
                result = current.result,
                onRescan = { viewModel.reset(); viewModel.startScan() },
                onBack = onBack
            )
            is CheckupStage.Failed -> FailedContent(
                message = current.message,
                onRetry = { viewModel.reset(); viewModel.startScan() },
                onBack = onBack
            )
        }
    }
}

// ---------------------------------------------------------------------
// Scanning stage
// ---------------------------------------------------------------------

@Composable
private fun ScanningContent(scanning: CheckupStage.Scanning) {
    val infiniteTransition = rememberInfiniteTransition(label = "checkupPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size((140 * pulse).dp)
                    .background(PhosphorGreen.copy(alpha = 0.10f), CircleShape)
            )
            CircularProgressIndicator(
                progress = { (scanning.stepIndex + 1) / scanning.totalSteps.toFloat() },
                modifier = Modifier.size(120.dp),
                strokeWidth = 6.dp,
                color = PhosphorGreen,
                trackColor = Hairline
            )
            Text(
                text = "${((scanning.stepIndex + 1) * 100 / scanning.totalSteps)}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Running Battery Checkup",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = scanning.stepLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = PanelGray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Step ${scanning.stepIndex + 1} of ${scanning.totalSteps}",
            style = MaterialTheme.typography.bodySmall,
            color = PanelGrayDim
        )
    }
}

// ---------------------------------------------------------------------
// Result stage
// ---------------------------------------------------------------------

@Composable
private fun ResultContent(
    result: BatteryCheckupScorer.CheckupResult,
    onRescan: () -> Unit,
    onBack: () -> Unit
) {
    val scoreColor = when {
        result.score >= 80 -> PhosphorGreen
        result.score >= 50 -> WarningAmber
        else -> CriticalRed
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            InstrumentCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("YOUR BATTERY SCORE", style = MaterialTheme.typography.labelSmall, color = PanelGrayDim)
                    Spacer(modifier = Modifier.height(14.dp))
                    RingMeter(
                        fraction = result.score / 100f,
                        value = "${result.score}",
                        unit = "/ 100",
                        color = scoreColor,
                        size = 160.dp,
                        strokeWidth = 12.dp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = scoreSummary(result.score),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PanelGray
                    )
                }
            }
        }

        item {
            Text(
                "What we found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(result.findings) { finding ->
            FindingCard(finding)
        }

        if (result.topDrainingApps.isNotEmpty()) {
            item {
                Text(
                    "Top apps using battery today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(result.topDrainingApps) { app ->
                InstrumentCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${(app.usageFraction * 100).toInt()}% of today's screen time" +
                                    (app.batteryPercent?.let { " · ~${it.toInt()}% battery" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = PanelGray
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onRescan,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = Color.Black)
                ) {
                    Text("Run Checkup Again", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text("Done", color = PhosphorGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun scoreSummary(score: Int): String = when {
    score >= 80 -> "Your battery settings are in great shape."
    score >= 50 -> "A few changes could meaningfully improve your battery life."
    else -> "Several settings are working against your battery life right now."
}

@Composable
private fun FindingCard(finding: BatteryCheckupScorer.Finding) {
    val (icon, color) = when (finding.severity) {
        BatteryCheckupScorer.Severity.GOOD -> Icons.Filled.CheckCircle to PhosphorGreen
        BatteryCheckupScorer.Severity.INFO -> Icons.Filled.Info to PanelGray
        BatteryCheckupScorer.Severity.WARNING -> Icons.Filled.Warning to WarningAmber
    }

    InstrumentCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(finding.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(finding.detail, style = MaterialTheme.typography.bodySmall, color = PanelGray)
                finding.suggestion?.let { suggestion ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Suggestion: $suggestion",
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Failure stage
// ---------------------------------------------------------------------

@Composable
private fun FailedContent(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Checkup couldn't finish", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = PanelGray)
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = Color.Black)
        ) {
            Text("Try Again", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.OutlinedButton(onClick = onBack, shape = MaterialTheme.shapes.extraLarge) {
            Text("Back", color = PhosphorGreen)
        }
    }
}
