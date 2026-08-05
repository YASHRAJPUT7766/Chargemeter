package com.yash.chargemeterpro.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.height
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.yash.chargemeterpro.MainActivity

/**
 * Fourth home-screen widget: today's total screen-on time and the single
 * most-used app so far today, pulled from the same UsageStatsRepository
 * that backs the in-app Usage dashboard (spec #6-#9).
 *
 * Usage data isn't pushed on every 15s charging-poll like the battery
 * widgets — screen time doesn't need that cadence and UsageStatsManager
 * queries are heavier than a battery snapshot read. Instead
 * [UsageWidgetUpdateWorker] refreshes this widget roughly every 30
 * minutes via WorkManager (mirroring DrainMonitorWorker's pattern), plus
 * once immediately whenever the app is opened (MainActivity.onCreate).
 *
 * If Usage Access hasn't been granted, shows a short "enable access"
 * prompt instead of fabricating zeros — tapping the widget opens the app
 * the same as the other widgets, where that permission can be granted.
 */
class UsageWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            UsageWidgetContent()
        }
    }
}

private val BgColor = Color(0xFF0A0E12)
private val PhosphorGreenColor = Color(0xFF39FF88)
private val PanelGrayColor = Color(0xFF8B98A5)
private val VoltageBlueColor = Color(0xFF5B8DEF)

private fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun UsageWidgetContent() {
    val prefs = currentState<Preferences>()
    val hasAccess = prefs[UsageWidgetKeys.HAS_ACCESS] ?: true
    val totalMillis = prefs[UsageWidgetKeys.TOTAL_FOREGROUND_MILLIS]
    val topAppName = prefs[UsageWidgetKeys.TOP_APP_NAME]
    val unlockCount = prefs[UsageWidgetKeys.UNLOCK_COUNT]

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(BgColor))
            .padding(12.dp)
            .clickable(actionStartActivity(android.content.Intent(LocalContext.current, MainActivity::class.java)))
    ) {
        if (!hasAccess) {
            Text(
                text = "Usage Access",
                style = TextStyle(color = ColorProvider(PhosphorGreenColor), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Tap to enable in app",
                style = TextStyle(color = ColorProvider(PanelGrayColor), fontSize = 11.sp)
            )
            return@Column
        }

        Text(
            text = totalMillis?.let { formatDuration(it) } ?: "—",
            style = TextStyle(color = ColorProvider(PhosphorGreenColor), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        )
        Text(
            text = "screen time today",
            style = TextStyle(color = ColorProvider(PanelGrayColor), fontSize = 11.sp)
        )

        Spacer(modifier = GlanceModifier.height(6.dp))

        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = "Top: ",
                style = TextStyle(color = ColorProvider(PanelGrayColor), fontSize = 11.sp)
            )
            Text(
                text = topAppName ?: "—",
                style = TextStyle(color = ColorProvider(VoltageBlueColor), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            )
        }

        if (unlockCount != null) {
            Text(
                text = "$unlockCount unlocks",
                style = TextStyle(color = ColorProvider(PanelGrayColor), fontSize = 10.sp)
            )
        }
    }
}

object UsageWidgetKeys {
    val HAS_ACCESS = androidx.datastore.preferences.core.booleanPreferencesKey("usage_has_access")
    val TOTAL_FOREGROUND_MILLIS = longPreferencesKey("usage_total_foreground_millis")
    val TOP_APP_NAME = stringPreferencesKey("usage_top_app_name")
    val UNLOCK_COUNT = intPreferencesKey("usage_unlock_count")
}

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()
}

/**
 * Pushes a fresh today-so-far usage summary into every placed instance
 * of this widget. Called from [com.yash.chargemeterpro.util.UsageWidgetUpdateWorker]
 * and once from MainActivity.onCreate for an immediate first paint.
 */
object UsageWidgetUpdater {

    suspend fun pushNoAccess(context: Context) {
        pushInternal(context) { prefs ->
            prefs[UsageWidgetKeys.HAS_ACCESS] = false
        }
    }

    suspend fun pushSummary(
        context: Context,
        totalForegroundMillis: Long,
        topAppName: String?,
        unlockCount: Int?
    ) {
        pushInternal(context) { prefs ->
            prefs[UsageWidgetKeys.HAS_ACCESS] = true
            prefs[UsageWidgetKeys.TOTAL_FOREGROUND_MILLIS] = totalForegroundMillis
            if (topAppName != null) prefs[UsageWidgetKeys.TOP_APP_NAME] = topAppName
            if (unlockCount != null) prefs[UsageWidgetKeys.UNLOCK_COUNT] = unlockCount
        }
    }

    private suspend fun pushInternal(context: Context, apply: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(UsageWidget::class.java)
        if (glanceIds.isEmpty()) return

        glanceIds.forEach { id ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply { apply(this) }
            }
            UsageWidget().update(context, id)
        }
    }
}
