package com.yash.chargemeterpro.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.chargemeterpro.data.local.entity.ChargingSessionEntity
import com.yash.chargemeterpro.ui.components.CompareBarRow
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.navigation.ScreenBackTopBar
import com.yash.chargemeterpro.ui.theme.GraphBatteryPct
import com.yash.chargemeterpro.ui.theme.Hairline
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PanelGrayDim
import com.yash.chargemeterpro.ui.theme.PhosphorGreen
import com.yash.chargemeterpro.ui.theme.VoltageBlue
import com.yash.chargemeterpro.ui.theme.WarningAmber
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
                SessionPickRow(session, selected = session.id == sessionAId, accentColor = PhosphorGreen) { sessionAId = session.id }
            }

            item {
                Text("Select Session B", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(completedSessions, key = { "B_${it.id}" }) { session ->
                SessionPickRow(session, selected = session.id == sessionBId, accentColor = VoltageBlue) { sessionBId = session.id }
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
private fun SessionPickRow(session: ChargingSessionEntity, selected: Boolean, accentColor: Color, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) accentColor.copy(alpha = 0.18f) else Hairline)
                .border(2.dp, if (selected) accentColor else PanelGrayDim, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accentColor))
            }
        }
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
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Comparison", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(label = "A", color = PhosphorGreen)
                    LegendDot(label = "B", color = VoltageBlue)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            CompareBarRow(
                label = "Average Power",
                valueA = a.averagePowerWatts?.toFloat(),
                valueB = b.averagePowerWatts?.toFloat(),
                displayA = a.averagePowerWatts?.let { "%.1fW".format(it) } ?: "—",
                displayB = b.averagePowerWatts?.let { "%.1fW".format(it) } ?: "—",
                colorA = PhosphorGreen,
                colorB = VoltageBlue
            )
            CompareBarRow(
                label = "Peak Power",
                valueA = a.maxPowerWatts?.toFloat(),
                valueB = b.maxPowerWatts?.toFloat(),
                displayA = a.maxPowerWatts?.let { "%.1fW".format(it) } ?: "—",
                displayB = b.maxPowerWatts?.let { "%.1fW".format(it) } ?: "—",
                colorA = WarningAmber,
                colorB = VoltageBlue
            )
            CompareBarRow(
                label = "Duration",
                valueA = a.endTimeMillis?.let { ((it - a.startTimeMillis) / 60000).toFloat() },
                valueB = b.endTimeMillis?.let { ((it - b.startTimeMillis) / 60000).toFloat() },
                displayA = a.endTimeMillis?.let { "${(it - a.startTimeMillis) / 60000}m" } ?: "—",
                displayB = b.endTimeMillis?.let { "${(it - b.startTimeMillis) / 60000}m" } ?: "—",
                colorA = PhosphorGreen,
                colorB = VoltageBlue
            )
            CompareBarRow(
                label = "Energy Delivered",
                valueA = a.estimatedEnergyWattHours?.toFloat(),
                valueB = b.estimatedEnergyWattHours?.toFloat(),
                displayA = a.estimatedEnergyWattHours?.let { "%.1fWh".format(it) } ?: "—",
                displayB = b.estimatedEnergyWattHours?.let { "%.1fWh".format(it) } ?: "—",
                colorA = GraphBatteryPct,
                colorB = VoltageBlue
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Plug Type", style = MaterialTheme.typography.labelMedium, color = PanelGray)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlugPill(a.plugTypeName, PhosphorGreen)
                    PlugPill(b.plugTypeName, VoltageBlue)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlugPill(value: String?, color: Color) {
    Row(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(value ?: "—", style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}
