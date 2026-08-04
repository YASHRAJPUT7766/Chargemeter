package com.yash.chargemeterpro.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.chargemeterpro.data.local.entity.ChargingSessionEntity
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PhosphorGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HistoryScreen(
    onOpenSession: (Long) -> Unit,
    onCompareSessions: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.shareEvents.collect { uri ->
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share charging report"))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = onCompareSessions) {
                    Icon(Icons.Filled.CompareArrows, contentDescription = "Compare sessions")
                }
                if (sessions.isNotEmpty()) {
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Filled.Share, contentDescription = "Export or share history")
                        }
                        DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Export as CSV") },
                                onClick = {
                                    showExportMenu = false
                                    viewModel.exportHistoryCsv()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export as PDF") },
                                onClick = {
                                    showExportMenu = false
                                    viewModel.exportHistoryPdf()
                                }
                            )
                        }
                    }
                    IconButton(onClick = { showClearAllDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear all history")
                    }
                }
            }
        }

        if (sessions.isEmpty()) {
            EmptyHistoryState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        onClick = { onOpenSession(session.id) },
                        onDelete = { viewModel.deleteSession(session.id) }
                    )
                }
                item { androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear all history?") },
            text = { Text("This permanently deletes every saved charging session from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllHistory()
                    showClearAllDialog = false
                }) { Text("Delete All", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptyHistoryState() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No charging sessions yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Plug in your device to start tracking your first charging session automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = PanelGray
            )
        }
    }
}

@Composable
private fun SessionRow(session: ChargingSessionEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.yash.chargemeterpro.ui.components.RingMeter(
                    fraction = ((session.averagePowerWatts ?: 0.0) / 30.0).toFloat(),
                    value = session.averagePowerWatts?.let { "%.0f".format(it) } ?: "—",
                    unit = "W",
                    color = PhosphorGreen,
                    size = 56.dp,
                    strokeWidth = 5.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        formatSessionDate(session.startTimeMillis),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    BatteryChangeBar(
                        startPercent = session.startBatteryPercent,
                        endPercent = session.endBatteryPercent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        formatDuration(session),
                        style = MaterialTheme.typography.labelSmall,
                        color = PanelGray
                    )
                }
            }

            var showDeleteConfirm by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = PanelGray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", style = MaterialTheme.typography.labelMedium, color = PanelGray)
                }
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete this session?") },
                    text = { Text("This charging session and its recorded samples will be permanently removed.") },
                    confirmButton = {
                        TextButton(onClick = {
                            onDelete()
                            showDeleteConfirm = false
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

/**
 * Visual battery-level-change indicator: a single capsule track with two
 * overlaid fills — a dim one at the session's start level, a bright one
 * at its end level — plus the numeric start/end percentages as small
 * labels at each end. Replaces the old plain "58% -> 92%" text with
 * something a user can read at a glance without parsing digits.
 */
@Composable
private fun BatteryChangeBar(startPercent: Int, endPercent: Int?) {
    val resolvedEnd = endPercent ?: startPercent
    val endFraction = (resolvedEnd / 100f).coerceIn(0f, 1f)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                .background(com.yash.chargemeterpro.ui.theme.Hairline)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = endFraction.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(com.yash.chargemeterpro.ui.theme.PhosphorGreenDim, PhosphorGreen)
                        )
                    )
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = if (endPercent != null) "$startPercent% → $endPercent%" else "$startPercent% → charging…",
            style = MaterialTheme.typography.labelSmall,
            color = PanelGray
        )
    }
}

private fun formatSessionDate(millis: Long): String {
    val fmt = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
    return fmt.format(Date(millis))
}

private fun formatDuration(session: ChargingSessionEntity): String {
    val end = session.endTimeMillis ?: return "ongoing"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(end - session.startTimeMillis)
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
