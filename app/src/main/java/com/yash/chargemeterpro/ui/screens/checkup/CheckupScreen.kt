package com.yash.chargemeterpro.ui.screens.checkup

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yash.chargemeterpro.domain.usecase.BatteryCheckupScorer
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.components.RingMeter
import com.yash.chargemeterpro.ui.theme.CriticalRed
import com.yash.chargemeterpro.ui.theme.Hairline
import com.yash.chargemeterpro.ui.theme.InstrumentSurface
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
        initialValue = 0.9f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Smoothly interpolate the ring/number toward the target percent
    // instead of snapping, so even the per-percent steps from the
    // ViewModel feel like a continuous climb rather than a tick.
    val animatedPercent by animateFloatAsState(
        targetValue = scanning.percent.toFloat(),
        animationSpec = tween(durationMillis = 260, easing = androidx.compose.animation.core.LinearEasing),
        label = "percentClimb"
    )

    val listState = rememberLazyListState()
    LaunchedEffect(scanning.log.size) {
        if (scanning.log.isNotEmpty()) {
            listState.animateScrollToItem(scanning.log.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size((124 * pulse).dp)
                    .background(PhosphorGreen.copy(alpha = 0.10f), CircleShape)
            )
            CircularProgressIndicator(
                progress = { animatedPercent / 100f },
                modifier = Modifier.size(104.dp),
                strokeWidth = 6.dp,
                color = PhosphorGreen,
                trackColor = Hairline
            )
            Text(
                text = "${animatedPercent.toInt()}%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Running Battery Checkup",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = scanning.stepLabel + "…",
            style = MaterialTheme.typography.bodyMedium,
            color = PhosphorGreen,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Step ${scanning.stepIndex + 1} of ${scanning.totalSteps}",
            style = MaterialTheme.typography.bodySmall,
            color = PanelGrayDim
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Live scrolling process log — mirrors a build/CI console so the
        // scan reads as genuinely working through real checks rather
        // than sitting on a blank spinner.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(InstrumentSurface)
                .border(1.dp, Hairline, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(scanning.log) { line ->
                    LogLineRow(line)
                }
                item { Spacer(modifier = Modifier.height(2.dp)) }
            }
        }
    }
}

@Composable
private fun LogLineRow(line: LogLine) {
    val (prefix, color) = when (line.kind) {
        LogKind.OK -> "✓" to PhosphorGreen
        LogKind.WARN -> "!" to WarningAmber
        LogKind.INFO -> "›" to PanelGrayDim
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = prefix,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = color,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text = line.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = if (line.kind == LogKind.WARN) WarningAmber.copy(alpha = 0.9f) else PanelGray
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
    val checkedCount = result.findings.size
    val warningCount = result.findings.count { it.severity == BatteryCheckupScorer.Severity.WARNING }
    val goodCount = result.findings.count { it.severity == BatteryCheckupScorer.Severity.GOOD }

    // Actionable fixes pulled straight from the findings that actually
    // have a suggestion attached — no invented tips layered on top of
    // what the scan genuinely found wrong.
    val actionableFindings = result.findings.filter {
        it.severity == BatteryCheckupScorer.Severity.WARNING && it.suggestion != null
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
                        color = PanelGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDividerLine()
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ScanTallyItem(count = checkedCount, label = "Checked", color = PanelGray)
                        ScanTallyItem(count = goodCount, label = "Good", color = PhosphorGreen)
                        ScanTallyItem(count = warningCount, label = "Needs attention", color = WarningAmber)
                    }
                }
            }
        }

        if (actionableFindings.isNotEmpty()) {
            item {
                Text(
                    "Quick wins to save battery",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(actionableFindings) { finding ->
                QuickWinCard(finding)
            }
        }

        item {
            Text(
                "Everything we checked",
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
private fun HorizontalDividerLine() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

@Composable
private fun ScanTallyItem(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = PanelGrayDim)
    }
}

/**
 * A single concrete, tappable-feeling action card built from a real
 * finding's suggestion — e.g. "Turn on Battery Saver". Distinct from
 * [FindingCard] below: this section is deliberately just the short list
 * of things worth doing right now, while "Everything we checked" further
 * down is the full transparent log of every check (good, info, warning).
 */
@Composable
private fun QuickWinCard(finding: BatteryCheckupScorer.Finding) {
    InstrumentCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(finding.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    finding.suggestion.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhosphorGreen,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
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
