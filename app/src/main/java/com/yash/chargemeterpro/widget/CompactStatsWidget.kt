package com.yash.chargemeterpro.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.yash.chargemeterpro.MainActivity
import com.yash.chargemeterpro.domain.model.ChargingStatus

/**
 * Third home-screen widget: a minimal 1x1-friendly tile for people who
 * just want battery % and current watts at a glance, without the fuller
 * instrument-panel readout ChargeMeterWidget shows (current/voltage/temp).
 * Reuses the exact same pushed state as ChargeMeterWidget — [WidgetKeys]
 * from ChargeMeterWidget.kt — since both widgets are fed the same
 * snapshot at the same time; no separate updater/data path needed here.
 */
class CompactStatsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            CompactStatsContent()
        }
    }
}

private val BgColor = Color(0xFF0A0E12)
private val PhosphorGreenColor = Color(0xFF39FF88)
private val PanelGrayColor = Color(0xFF8B98A5)

@Composable
private fun CompactStatsContent() {
    val prefs = currentState<Preferences>()
    val batteryPercent = prefs[WidgetKeys.BATTERY_PERCENT] ?: -1
    val statusName = prefs[WidgetKeys.STATUS] ?: ChargingStatus.UNKNOWN.name
    val wattsW = prefs[WidgetKeys.WATTS_W]

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(BgColor))
            .padding(10.dp)
            .clickable(actionStartActivity(android.content.Intent(LocalContext.current, MainActivity::class.java))),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = if (batteryPercent >= 0) "$batteryPercent%" else "—",
            style = TextStyle(color = ColorProvider(PhosphorGreenColor), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        )
        Text(
            text = wattsW?.let { "%.1fW".format(it) } ?: "— W",
            style = TextStyle(color = ColorProvider(PanelGrayColor), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        )
    }
}

class CompactStatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactStatsWidget()
}
