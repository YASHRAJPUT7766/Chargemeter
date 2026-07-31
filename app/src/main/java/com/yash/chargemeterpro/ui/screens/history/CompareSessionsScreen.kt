package com.yash.chargemeterpro.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.yash.chargemeterpro.data.local.entity.ChargingSessionEntity
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.navigation.ScreenBackTopBar
import com.yash.chargemeterpro.ui.theme.PanelGray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CompareSessionsScreen(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val completedSessions = remember(sessions) { sessions.filter { it.endTimeMillis != null } }

    var sessionAId by remember { mutableStateOf<Long?>(null) }
    var sessionBId by remember { mutableStateOf<Long?>(null) }

    val sessionA = completedSessions.firstOrNull { it.id == sessionAId }
    val sessionB = completedSessions.firstOrNull { it.id == sessionBId }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenBackTopBar(title = "Compare Sessions", onBack = onBack)

        if (completedSessions.size < 2) {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                item {
                    Text(
                        "You need at least two completed charging sessions to compare. Keep charging your device to build up history.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PanelGray
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Select Session A", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(completedSessions, key = { "A_${it.id}" }) { session ->
                SessionPickRow(session, selected = session.id == sessionAId) { sessionAId = session.id }
            }

            item {
                Text("Select Session B", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(completedSessions, key = { "B_${it.id}" }) { session ->
                SessionPickRow(session, selected = session.id == sessionBId) { sessionBId = session.id }
            }

            if (sessionA != null && sessionB != null) {
                item {
                    ComparisonTable(sessionA, sessionB)
                }
            }
        }
    }
}

@Composable
private fun SessionPickRow(session: ChargingSessionEntity, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column {
            val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            Text(fmt.format(Date(session.startTimeMillis)), style = MaterialTheme.typography.bodyLarge)
            Text(
                "${session.startBatteryPercent}% → ${session.endBatteryPercent}%  ·  ${session.averagePowerWatts?.let { "%.1fW avg".format(it) } ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = PanelGray
            )
        }
    }
}

@Composable
private fun ComparisonTable(a: ChargingSessionEntity, b: ChargingSessionEntity) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Comparison", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("", modifier = Modifier.weight(1f))
                Text("A", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = PanelGray)
                Text("B", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = PanelGray)
            }
            ComparisonRow("Avg Power", a.averagePowerWatts?.let { "%.1fW".format(it) }, b.averagePowerWatts?.let { "%.1fW".format(it) })
            ComparisonRow("Max Power", a.maxPowerWatts?.let { "%.1fW".format(it) }, b.maxPowerWatts?.let { "%.1fW".format(it) })
            ComparisonRow(
                "Duration",
                a.endTimeMillis?.let { "${(it - a.startTimeMillis) / 60000}m" },
                b.endTimeMillis?.let { "${(it - b.startTimeMillis) / 60000}m" }
            )
            ComparisonRow(
                "Energy",
                a.estimatedEnergyWattHours?.let { "%.1fWh".format(it) },
                b.estimatedEnergyWattHours?.let { "%.1fWh".format(it) }
            )
            ComparisonRow("Plug Type", a.plugTypeName, b.plugTypeName)
        }
    }
}

@Composable
private fun ComparisonRow(label: String, valueA: String?, valueB: String?) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = PanelGray)
        Text(valueA ?: "—", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(valueB ?: "—", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}
