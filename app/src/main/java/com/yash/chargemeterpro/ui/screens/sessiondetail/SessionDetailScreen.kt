package com.yash.chargemeterpro.ui.screens.sessiondetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.chargemeterpro.domain.usecase.PowerTerminology
import com.yash.chargemeterpro.ui.components.DisclaimerText
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.components.LiveLineChart
import com.yash.chargemeterpro.ui.components.ReadingRow
import com.yash.chargemeterpro.ui.screens.livemonitor.GraphPoint
import com.yash.chargemeterpro.ui.theme.GraphWattage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            title = { Text("Session Detail") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(androidx.compose.material.icons.Icons.Filled.ArrowBack, contentDescription = "Back")
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
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReadingRow(label = "Start Battery", value = session.startBatteryPercent.toString(), unit = "%")
                        ReadingRow(label = "End Battery", value = session.endBatteryPercent?.toString(), unit = "%")
                        ReadingRow(label = "Plug Type", value = session.plugTypeName)
                        ReadingRow(
                            label = "Duration",
                            value = session.endTimeMillis?.let {
                                val minutes = (it - session.startTimeMillis) / 60000
                                "${minutes / 60}h ${minutes % 60}m"
                            }
                        )
                        ReadingRow(
                            label = "Average Current",
                            value = session.averageCurrentMilliAmps?.let { "%.0f".format(it) },
                            unit = "mA"
                        )
                        ReadingRow(
                            label = "Average Power",
                            value = session.averagePowerWatts?.let { "%.2f".format(it) },
                            unit = "W"
                        )
                        ReadingRow(
                            label = "Max Power",
                            value = session.maxPowerWatts?.let { "%.2f".format(it) },
                            unit = "W"
                        )
                        ReadingRow(
                            label = "Max Current",
                            value = session.maxCurrentMilliAmps?.let { "%.0f".format(it) },
                            unit = "mA"
                        )
                        ReadingRow(
                            label = "Temperature Range",
                            value = if (session.minTemperatureCelsius != null && session.maxTemperatureCelsius != null) {
                                "%.1f – %.1f".format(session.minTemperatureCelsius, session.maxTemperatureCelsius)
                            } else null,
                            unit = "°C"
                        )
                        ReadingRow(
                            label = "Estimated Energy",
                            value = session.estimatedEnergyWattHours?.let { "%.2f".format(it) },
                            unit = "Wh"
                        )
                    }
                }
            }

            item {
                InstrumentCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "Wattage Over This Session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val points = state.samples.mapNotNull { s ->
                            s.powerWatts?.let { GraphPoint(s.timestampMillis, it.toFloat()) }
                        }
                        LiveLineChart(points = points, lineColor = GraphWattage, unitSuffix = "W")
                    }
                }
            }

            item {
                DisclaimerText(text = PowerTerminology.WATTAGE_ESTIMATE_DISCLAIMER)
            }
        }
    }
}
