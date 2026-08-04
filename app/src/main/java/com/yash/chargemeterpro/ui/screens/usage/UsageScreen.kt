package com.yash.chargemeterpro.ui.screens.usage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.chargemeterpro.data.usage.AppUsageInfo
import com.yash.chargemeterpro.data.usage.UsagePermissionState
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.components.RingMeter
import com.yash.chargemeterpro.ui.components.SparklineGraph
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PanelGrayDim
import com.yash.chargemeterpro.ui.theme.PhosphorGreen
import com.yash.chargemeterpro.ui.theme.VoltageBlue
import com.yash.chargemeterpro.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Daily phone-usage dashboard (spec items #6-#9): a Digital Wellbeing-
 * style screen with a swipeable day pager (up to 15 days back), a large
 * donut showing total screen time for the selected day, and a per-app
 * breakdown list. Requires the special PACKAGE_USAGE_STATS app op —
 * UsagePermissionGate below handles the request flow when it's missing.
 */
@Composable
fun UsageScreen(
    onOpenApp: (packageName: String) -> Unit,
    viewModel: UsageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-check permission whenever this composable re-enters composition
    // fresh (e.g. returning from the system Usage Access settings screen
    // via back navigation), since there's no in-process callback for that
    // grant — it's a system settings toggle, not an Activity result.
    LaunchedEffect(Unit) { viewModel.refreshPermissionAndLoad() }

    // Keeps today's total screen time and per-app list advancing while
    // this screen stays open, instead of freezing at whatever the totals
    // were the moment the screen first loaded. refreshIfToday() itself
    // no-ops on any day other than today, so this is a cheap poll even
    // while the user has swiped back to a past day. LaunchedEffect ties
    // this loop to composition — leaving the screen cancels it
    // automatically, so it never polls while not visible. 30s matches
    // the cadence Digital-Wellbeing-style screens typically use: live
    // enough to feel real-time without hammering UsageStatsManager.
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(30_000)
            viewModel.refreshIfToday()
        }
    }

    when (state.permissionState) {
        UsagePermissionState.NOT_GRANTED -> UsagePermissionGate(
            onGrantClick = {
                val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        )
        UsagePermissionState.UNKNOWN -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PhosphorGreen)
        }
        UsagePermissionState.GRANTED -> UsageContent(
            state = state,
            onSelectDay = viewModel::selectDay,
            onPrevDay = viewModel::goToPreviousDay,
            onNextDay = viewModel::goToNextDay,
            onOpenApp = onOpenApp
        )
    }
}

@Composable
private fun UsagePermissionGate(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.LockClock, contentDescription = null, tint = PhosphorGreen, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Usage access needed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "ChargeFlow needs Usage Access permission to show your daily screen time and app-by-app breakdown. " +
                "This is a system permission — grant it on the next screen, then come back here.",
            style = MaterialTheme.typography.bodyMedium,
            color = PanelGray,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGrantClick) {
            Text("Open Usage Access Settings")
        }
    }
}

@Composable
private fun UsageContent(
    state: UsageUiState,
    onSelectDay: (Long) -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenApp: (String) -> Unit
) {
    val dayCount = (state.earliestEpochDay..LocalDate.now().toEpochDay()).count()
    val pagerState = rememberPagerState(
        initialPage = (state.selectedEpochDay - state.earliestEpochDay).toInt(),
        pageCount = { dayCount }
    )

    // Pager -> state: user physically swiped, so tell the ViewModel which
    // day is now selected.
    LaunchedEffect(pagerState.currentPage) {
        val epochDay = state.earliestEpochDay + pagerState.currentPage
        if (epochDay != state.selectedEpochDay) onSelectDay(epochDay)
    }

    // State -> pager: the prev/next arrow buttons in DaySelectorRow change
    // selectedEpochDay directly (not via a swipe), so the pager needs to
    // follow along and animate to match — otherwise tapping the arrows
    // would update the header label but leave the swiped content showing
    // the wrong day.
    LaunchedEffect(state.selectedEpochDay) {
        val targetPage = (state.selectedEpochDay - state.earliestEpochDay).toInt()
        if (targetPage in 0 until dayCount && targetPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DaySelectorRow(
            selectedEpochDay = state.selectedEpochDay,
            canGoPrevious = state.selectedEpochDay > state.earliestEpochDay,
            canGoNext = state.selectedEpochDay < LocalDate.now().toEpochDay(),
            onPrevDay = onPrevDay,
            onNextDay = onNextDay
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val epochDay = state.earliestEpochDay + page
            val summary = if (epochDay == state.selectedEpochDay) state.summary else null
            val isLoading = epochDay == state.selectedEpochDay && state.isLoading

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    UsageDonutCard(
                        totalMillis = summary?.totalForegroundTimeMillis ?: 0L,
                        hourlyBuckets = summary?.hourlyBuckets ?: List(24) { 0L },
                        isLoading = isLoading
                    )
                }
                item {
                    UsageStatsRow(
                        appCount = summary?.appCount ?: 0,
                        batteryDrop = summary?.batteryDropPercent,
                        unlockCount = summary?.unlockCount
                    )
                }
                if (!isLoading && summary != null && summary.apps.isEmpty()) {
                    item { EmptyUsageCard() }
                }
                if (summary != null && summary.apps.isNotEmpty()) {
                    item {
                        Text(
                            "App usage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(summary.apps, key = { it.packageName }) { app ->
                        AppUsageRow(app = app, onClick = { onOpenApp(app.packageName) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySelectorRow(
    selectedEpochDay: Long,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit
) {
    val date = LocalDate.ofEpochDay(selectedEpochDay)
    val today = LocalDate.now()
    val label = when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevDay, enabled = canGoPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day", tint = if (canGoPrevious) PhosphorGreen else PanelGrayDim)
        }
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onNextDay, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day", tint = if (canGoNext) PhosphorGreen else PanelGrayDim)
        }
    }
}

@Composable
private fun UsageDonutCard(totalMillis: Long, hourlyBuckets: List<Long>, isLoading: Boolean) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (totalMillis == 0L && !isLoading) "No usage recorded" else "Total screen time",
                        style = MaterialTheme.typography.labelLarge,
                        color = PanelGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (!isLoading) {
                        Text(
                            formatDuration(totalMillis),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = PhosphorGreen
                        )
                    }
                }
                if (isLoading) {
                    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PhosphorGreen)
                    }
                } else {
                    // 8h reference ceiling — a typical-range visual cue, not a hard max.
                    val fraction = (totalMillis.toFloat() / (8 * 60 * 60 * 1000f)).coerceIn(0.02f, 1f)
                    RingMeter(
                        fraction = fraction,
                        value = "",
                        unit = null,
                        color = PhosphorGreen,
                        size = 96.dp,
                        strokeWidth = 12.dp
                    )
                }
            }

            if (!isLoading) {
                Spacer(modifier = Modifier.height(20.dp))
                val hourlyMinutes = hourlyBuckets.map { it / 60000f }
                SparklineGraph(
                    values = hourlyMinutes,
                    color = PhosphorGreen,
                    height = 90.dp,
                    showMinMax = false
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("12 AM", style = MaterialTheme.typography.labelSmall, color = PanelGrayDim)
                    Text("6 AM", style = MaterialTheme.typography.labelSmall, color = PanelGrayDim)
                    Text("12 PM", style = MaterialTheme.typography.labelSmall, color = PanelGrayDim)
                    Text("6 PM", style = MaterialTheme.typography.labelSmall, color = PanelGrayDim)
                    Text("Now", style = MaterialTheme.typography.labelSmall, color = PanelGrayDim)
                }
            }
        }
    }
}

@Composable
private fun UsageStatsRow(appCount: Int, batteryDrop: Int?, unlockCount: Int?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(modifier = Modifier.weight(1f), label = "Apps used", value = "$appCount", color = VoltageBlue)
        StatCard(modifier = Modifier.weight(1f), label = "Battery used", value = batteryDrop?.let { "$it%" } ?: "—", color = WarningAmber)
        StatCard(modifier = Modifier.weight(1f), label = "Unlocks", value = unlockCount?.toString() ?: "—", color = PhosphorGreen)
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    InstrumentCard(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = PanelGray, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun EmptyUsageCard() {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Icon(Icons.Filled.Apps, contentDescription = null, tint = PanelGrayDim, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("No app usage recorded for this day", color = PanelGray, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AppUsageRow(app: AppUsageInfo, onClick: () -> Unit) {
    InstrumentCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            AppIconImage(app = app, size = 40.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        "${(app.usageFraction * 100).let { if (it < 1f && it > 0f) "<1" else "%.0f".format(it) }}% of total usage",
                        style = MaterialTheme.typography.labelSmall,
                        color = PanelGray
                    )
                    app.batteryPercent?.let {
                        Text(
                            "  ·  ~${"%.0f".format(it)}% battery",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber
                        )
                    }
                }
            }
            Text(
                formatDuration(app.foregroundTimeMillis),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = PhosphorGreen
            )
        }
    }
}

@Composable
fun AppIconImage(app: AppUsageInfo, size: androidx.compose.ui.unit.Dp) {
    val bitmap = remember(app.packageName) { app.icon?.let { drawableToBitmap(it) } }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = app.appName,
            modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(PanelGrayDim.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Apps, contentDescription = app.appName, tint = PanelGray, modifier = Modifier.size(size / 2))
        }
    }
}

private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap {
    if (drawable is android.graphics.drawable.BitmapDrawable) return drawable.bitmap
    val width = drawable.intrinsicWidth.coerceAtLeast(1)
    val height = drawable.intrinsicHeight.coerceAtLeast(1)
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
