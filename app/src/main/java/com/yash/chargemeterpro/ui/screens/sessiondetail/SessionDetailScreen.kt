package com.yash.chargemeterpro.ui.screens.sessiondetail

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
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
import com.yash.chargemeterpro.ui.components.StatusBadgeRow
import com.yash.chargemeterpro.ui.theme.GraphTemperature
import com.yash.chargemeterpro.ui.theme.Hairline
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PhosphorGreen
import com.yash.chargemeterpro.ui.theme.PhosphorGreenDim
import com.yash.chargemeterpro.ui.theme.VoltageBlue
import com.yash.chargemeterpro.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A completed session's summary — redesigned around rings/capsule bars
 * instead of a plotted "wattage over time" line chart, per the
 * ChargeFlow spec's no-line-charts rule. The underlying per-sample data
 * (state.samples) is still collected and still exportable via CSV/PDF —
 * nothing about session recording was removed, just how the power
 * range is *visualized* on this screen: as a min/avg/max comparison
 * instead of a time-series plot.
 */
@Composable
fun SessionDetailScreen(sessionId: Long, onBack: () -> Unit, viewModel: SessionDetailViewModel = hiltViewModel()) {
    LaunchedEffect(sessionId) { viewModel.load(sessionId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = state.session
    val context = LocalContext.current
    var showShareMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.shareEvents.collect { uri ->
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share session report"))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.TopAppBar(
            title = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(
                            id = com.yash.chargemeterpro.R.drawable.ic_launcher_foreground
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Text("Session Detail", modifier = Modifier.padding(start = 10.dp))
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (session != null) {
                    Box {
                        IconButton(onClick = { showShareMenu = true }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share or export session")
                        }
                        DropdownMenu(expanded = showShareMenu, onDismissRequest = { showShareMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Share as PDF") },
                                onClick = {
                                    showShareMenu = false
                                    viewModel.shareAsPdf()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export samples as CSV") },
                                onClick = {
                                    showShareMenu = false
                                    viewModel.exportSamplesAsCsv()
                                }
                            )
                        }
                    }
                }
            }
        )

        if (session == null) {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                item { Text(if (state.isLoading) "Loading…" else "Session not found") }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                val fmt = SimpleDateFormat("EEEE, MMM d, yyyy · h:mm a", Locale.getDefault())
                Text(
                    fmt.format(Date(session.startTimeMillis)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                InstrumentCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            RingMeter(
                                fraction = ((session.averagePowerWatts ?: 0.0) / 30.0).toFloat(),
                                value = session.averagePowerWatts?.let { "%.1f".format(it) } ?: "—",
                                unit = "AVG W",
                                color = PhosphorGreen
                            )
                            RingMeter(
                                fraction = ((session.maxPowerWatts ?: 0.0) / 30.0).toFloat(),
                                value = session.maxPowerWatts?.let { "%.1f".format(it) } ?: "—",
                                unit = "PEAK W",
                                color = WarningAmber
                            )
                            RingMeter(
                                fraction = ((session.averageCurrentMilliAmps ?: 0.0) / 3000.0).toFloat(),
                                value = session.averageCurrentMilliAmps?.let { "%.0f".format(it) } ?: "—",
                                unit = "AVG mA",
                                color = VoltageBlue
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        BatteryChangeBar(
                            startPercent = session.startBatteryPercent,
                            endPercent = session.endBatteryPercent
                        )

                        StatusBadgeRow(label = "Plug Type", value = session.plugTypeName, accentColor = PhosphorGreen)

                        StatusBadgeRow(
                            label = "Duration",
                            value = session.endTimeMillis?.let {
                                val minutes = (it - session.startTimeMillis) / 60000
                                "${minutes / 60}h ${minutes % 60}m"
                            },
                            accentColor = VoltageBlue
                        )

                        CapsuleMeterRow(
                            label = "Max Current",
                            valueText = session.maxCurrentMilliAmps?.let { "%.0f mA".format(it) } ?: "—",
                            fraction = ((session.maxCurrentMilliAmps ?: 0.0) / 3000.0).toFloat(),
                            color = WarningAmber
                        )
                        CapsuleMeterRow(
                            label = "Estimated Energy",
                            valueText = session.estimatedEnergyWattHours?.let { "%.2f Wh".format(it) } ?: "—",
                            fraction = ((session.estimatedEnergyWattHours ?: 0.0) / 20.0).toFloat(),
                            color = PhosphorGreen
                        )

                        if (session.minTemperatureCelsius != null && session.maxTemperatureCelsius != null) {
                            CapsuleMeterRow(
                                label = "Temperature Range",
                                valueText = "%.1f – %.1f°C".format(session.minTemperatureCelsius, session.maxTemperatureCelsius),
                                fraction = ((session.maxTemperatureCelsius) / 45.0).toFloat(),
                                color = GraphTemperature
                            )
                        }
                    }
                }
            }

            if (state.samples.size >= 2) {
                item {
                    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
                        SparklineGraph(
                            values = state.samples.mapNotNull { it.powerWatts?.toFloat() },
                            color = PhosphorGreen,
                            label = "Power Over Session",
                            valueSuffix = " W",
                            height = 120.dp
                        )
                    }
                }
            }

            item {
                DisclaimerText(text = PowerTerminology.WATTAGE_ESTIMATE_DISCLAIMER)
            }
        }
    }
}

@Composable
private fun BatteryChangeBar(startPercent: Int, endPercent: Int?) {
    val resolvedEnd = endPercent ?: startPercent
    val endFraction = (resolvedEnd / 100f).coerceIn(0f, 1f)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Battery Change", style = MaterialTheme.typography.bodyMedium, color = PanelGray)
            Text(
                text = if (endPercent != null) "$startPercent% → $endPercent%" else "$startPercent% → …",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = PhosphorGreen
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Hairline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = endFraction.coerceAtLeast(0.02f))
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(PhosphorGreenDim, PhosphorGreen)))
            )
        }
    }
}
