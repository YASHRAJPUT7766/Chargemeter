package com.yash.chargemeterpro.ui.screens.appdetail

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.components.SparklineGraph
import com.yash.chargemeterpro.ui.navigation.ScreenBackTopBar
import com.yash.chargemeterpro.ui.screens.usage.formatDuration
import com.yash.chargemeterpro.ui.theme.CriticalRed
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PanelGrayDim
import com.yash.chargemeterpro.ui.theme.PhosphorGreen
import com.yash.chargemeterpro.ui.theme.VoltageBlue
import com.yash.chargemeterpro.ui.theme.WarningAmber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Per-app usage detail (spec item #9) plus App Restrictions (spec item
 * #10, UI + tracking only — see AppLimitsDataStore doc for why real
 * blocking/enforcement is deliberately out of scope).
 */
@Composable
fun AppDetailScreen(
    onBack: () -> Unit,
    viewModel: AppDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLimitDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenBackTopBar(title = state.appName.ifBlank { "App usage" }, onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { AppHeaderCard(state) }
            item { PeriodSelector(selected = state.selectedPeriod, onSelect = viewModel::selectPeriod) }
            item { UsageHistoryCard(state) }
            item {
                RestrictionCard(
                    limitMinutes = state.limitMinutes,
                    limitEnabled = state.limitEnabled,
                    todayMinutes = ((state.todayUsage?.foregroundTimeMillis ?: 0L) / 60000).toInt(),
                    onToggleEnabled = viewModel::setLimitEnabled,
                    onEditClick = { showLimitDialog = true },
                    onRemoveClick = viewModel::removeLimit
                )
            }
        }
    }

    if (showLimitDialog) {
        SetLimitDialog(
            initialMinutes = state.limitMinutes ?: 60,
            onDismiss = { showLimitDialog = false },
            onConfirm = { minutes ->
                viewModel.setLimit(minutes)
                showLimitDialog = false
            }
        )
    }
}

@Composable
private fun AppHeaderCard(state: AppDetailUiState) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            val bitmap = remember(state.packageName) { state.icon?.let { drawableToBitmapSafe(it) } }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = state.appName,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(PanelGrayDim.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Apps, contentDescription = null, tint = PanelGray, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(state.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatColumn(
                    icon = Icons.Filled.Timer,
                    label = "Today",
                    value = formatDuration(state.todayUsage?.foregroundTimeMillis ?: 0L),
                    color = PhosphorGreen
                )
                StatColumn(
                    icon = Icons.Filled.TouchApp,
                    label = "Opens",
                    value = "${state.todayUsage?.launchCount ?: 0}",
                    color = VoltageBlue
                )
                state.todayUsage?.batteryPercent?.let {
                    StatColumn(
                        icon = Icons.Filled.Apps,
                        label = "Battery (est.)",
                        value = "${"%.0f".format(it)}%",
                        color = WarningAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = PanelGray)
    }
}

@Composable
private fun PeriodSelector(selected: AppDetailPeriod, onSelect: (AppDetailPeriod) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppDetailPeriod.entries.forEach { period ->
            FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = { Text(period.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PhosphorGreen.copy(alpha = 0.18f),
                    selectedLabelColor = PhosphorGreen
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun UsageHistoryCard(state: AppDetailUiState) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("Usage history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = PanelGrayDim, style = MaterialTheme.typography.labelMedium)
                }
            } else if (state.history.all { it.foregroundTimeMillis == 0L }) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("No usage in this period", color = PanelGrayDim, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                SparklineGraph(
                    values = state.history.map { (it.foregroundTimeMillis / 60000f) },
                    color = PhosphorGreen,
                    valueSuffix = "m",
                    showMinMax = true,
                    pointLabels = state.history.map {
                        LocalDate.ofEpochDay(it.dateEpochDay).format(DateTimeFormatter.ofPattern("d MMM"))
                    }
                )
                if (state.history.size <= 10) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            LocalDate.ofEpochDay(state.history.first().dateEpochDay).format(DateTimeFormatter.ofPattern("d MMM")),
                            style = MaterialTheme.typography.labelSmall,
                            color = PanelGrayDim
                        )
                        Text(
                            LocalDate.ofEpochDay(state.history.last().dateEpochDay).format(DateTimeFormatter.ofPattern("d MMM")),
                            style = MaterialTheme.typography.labelSmall,
                            color = PanelGrayDim
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestrictionCard(
    limitMinutes: Int?,
    limitEnabled: Boolean,
    todayMinutes: Int,
    onToggleEnabled: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Daily limit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Track time against a goal for this app",
                        style = MaterialTheme.typography.labelSmall,
                        color = PanelGray
                    )
                }
                if (limitMinutes != null) {
                    Switch(checked = limitEnabled, onCheckedChange = onToggleEnabled)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (limitMinutes == null) {
                OutlinedButton(onClick = onEditClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Set a daily limit")
                }
            } else {
                val fraction = (todayMinutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f)
                val overLimit = todayMinutes >= limitMinutes
                Text(
                    "${todayMinutes}m of ${limitMinutes}m used today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overLimit) CriticalRed else PanelGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (overLimit) CriticalRed else PhosphorGreen,
                    trackColor = PanelGrayDim.copy(alpha = 0.25f)
                )
                if (overLimit && limitEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "You've reached today's limit for this app. Battery Stats tracks this but won't block or close the app automatically.",
                        style = MaterialTheme.typography.labelSmall,
                        color = CriticalRed
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onEditClick) { Text("Edit") }
                    TextButton(onClick = onRemoveClick) { Text("Remove", color = CriticalRed) }
                }
            }
        }
    }
}

@Composable
private fun SetLimitDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(initialMinutes.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set daily limit") },
        text = {
            Column {
                Text("Minutes per day", style = MaterialTheme.typography.labelMedium, color = PanelGray)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = text,
                    onValueChange = { input -> if (input.all { it.isDigit() } && input.length <= 4) text = input },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val minutes = text.toIntOrNull()?.coerceIn(1, 1440) ?: 60
                onConfirm(minutes)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun drawableToBitmapSafe(drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap? {
    return try {
        if (drawable is android.graphics.drawable.BitmapDrawable) return drawable.bitmap
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    } catch (_: Exception) {
        null
    }
}
