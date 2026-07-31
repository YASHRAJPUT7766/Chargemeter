package com.yash.chargemeterpro.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.yash.chargemeterpro.MainActivity
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.model.ChargingStatus
import com.yash.chargemeterpro.domain.usecase.PowerCalculator

/**
 * ⚠️ VERIFY-BEFORE-SHIPPING NOTE: Glance's AppWidget API (androidx.glance
 * 1.1.1, declared in app/build.gradle.kts) is younger and has moved more
 * between versions than most Jetpack libraries. The composable structure
 * below (Column/Row/Text/GlanceModifier, PreferencesGlanceStateDefinition
 * for widget-local persisted state, updateAppWidgetState) reflects
 * Glance's documented 1.1.x pattern. Two things are worth confirming
 * once this is open in Android Studio:
 *   1. `actionStartActivity` is imported here from
 *      androidx.glance.appwidget.action, which is correct for 1.1.x —
 *      if the IDE reports it unresolved, the fallback location is
 *      androidx.glance.action (used in some earlier Glance versions).
 *   2. `androidx.glance.unit.ColorProvider` vs `androidx.glance.color.ColorProvider`
 *      — Glance has both a general ColorProvider and a
 *      day/night-aware one; the simple single-color usage here wants the
 *      basic one, but package paths for it have shifted across betas too.
 * If either fails to resolve, Glance's own samples at
 * https://github.com/android/user-interface-samples/tree/main/AppWidget
 * are the fastest way to confirm the exact import for whatever Glance
 * patch version Gradle actually resolves.
 *
 * Home screen widget backing feature #12. Built with Glance (Compose-based)
 * rather than classic RemoteViews/XML — Google's current recommended
 * approach for new widgets, and it lets this widget share the same
 * mental model (if not literal color-token file, since Glance can't
 * consume MaterialTheme) as the rest of the app.
 *
 * UPDATE CADENCE: see charge_meter_widget_info.xml for why
 * android:updatePeriodMillis is intentionally 0 — this widget is instead
 * pushed fresh data via [ChargeMeterWidgetUpdater.pushUpdate], called
 * from ChargingMonitorService's poll loop (~every 15s while Always-On
 * Monitor is active and the device is charging). Without Always-On
 * Monitor enabled, the widget reflects whatever the app last saw the
 * last time it was open — Android does not allow arbitrary background
 * polling purely to keep a widget fresh outside of a foreground service
 * or WorkManager's own rate limits, and this widget intentionally
 * doesn't try to work around that with a battery-costly workaround.
 */
class ChargeMeterWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent()
        }
    }
}

private val BgColor = Color(0xFF0A0E12)
private val PhosphorGreenColor = Color(0xFF39FF88)
private val PanelGrayColor = Color(0xFF8B98A5)
private val WarningAmberColor = Color(0xFFFF6B4A)
private val VoltageBlueColor = Color(0xFF5B8DEF)
private val TempColor = Color(0xFFFF8A5C)

@Composable
private fun WidgetContent() {
    val prefs = currentState<Preferences>()
    val batteryPercent = prefs[WidgetKeys.BATTERY_PERCENT] ?: -1
    val statusName = prefs[WidgetKeys.STATUS] ?: ChargingStatus.UNKNOWN.name
    val currentMa = prefs[WidgetKeys.CURRENT_MA]
    val voltageV = prefs[WidgetKeys.VOLTAGE_V]
    val wattsW = prefs[WidgetKeys.WATTS_W]
    val tempC = prefs[WidgetKeys.TEMP_C]

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(BgColor))
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = if (batteryPercent >= 0) "$batteryPercent%" else "—",
                style = TextStyle(color = ColorProvider(PhosphorGreenColor), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = statusLabel(statusName),
                style = TextStyle(color = ColorProvider(PanelGrayColor), fontSize = 11.sp)
            )
        }

        Spacer(modifier = GlanceModifier.width(4.dp))

        Text(
            text = wattsW?.let { "%.1f W".format(it) } ?: "— W",
            style = TextStyle(color = ColorProvider(PhosphorGreenColor), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        )

        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = currentMa?.let { "%.0fmA".format(it) } ?: "—",
                style = TextStyle(color = ColorProvider(WarningAmberColor), fontSize = 11.sp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = voltageV?.let { "%.2fV".format(it) } ?: "—",
                style = TextStyle(color = ColorProvider(VoltageBlueColor), fontSize = 11.sp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = tempC?.let { "%.1f°C".format(it) } ?: "—",
                style = TextStyle(color = ColorProvider(TempColor), fontSize = 11.sp)
            )
        }
    }
}

private fun statusLabel(name: String) = when (name) {
    ChargingStatus.CHARGING.name -> "CHARGING"
    ChargingStatus.FULL.name -> "FULL"
    ChargingStatus.DISCHARGING.name -> "DISCHARGING"
    ChargingStatus.NOT_CHARGING.name -> "NOT CHARGING"
    else -> "—"
}

object WidgetKeys {
    val BATTERY_PERCENT = intPreferencesKey("battery_percent")
    val STATUS = stringPreferencesKey("status")
    val CURRENT_MA = doublePreferencesKey("current_ma")
    val VOLTAGE_V = doublePreferencesKey("voltage_v")
    val WATTS_W = doublePreferencesKey("watts_w")
    val TEMP_C = doublePreferencesKey("temp_c")
}

class ChargeMeterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChargeMeterWidget()
}

/**
 * Pushes a fresh [BatterySnapshot] into every currently-placed instance
 * of the widget. Called from ChargingMonitorService's poll loop. This is
 * a plain object (not Hilt-injected) since PowerCalculator is itself a
 * stateless object — no DI container needed to reach it.
 */
object ChargeMeterWidgetUpdater {

    suspend fun pushUpdate(context: Context, snapshot: BatterySnapshot) {
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(ChargeMeterWidget::class.java)
        if (glanceIds.isEmpty()) return // no widget placed on any home screen — skip the state write entirely

        val watts = PowerCalculator.batteryInputPowerWatts(snapshot)

        glanceIds.forEach { id ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetKeys.BATTERY_PERCENT] = snapshot.batteryPercent
                    this[WidgetKeys.STATUS] = snapshot.chargingStatus.name
                    snapshot.currentMilliAmpsNormalized?.let { this[WidgetKeys.CURRENT_MA] = it }
                    snapshot.voltageVolts?.let { this[WidgetKeys.VOLTAGE_V] = it }
                    watts?.let { this[WidgetKeys.WATTS_W] = it }
                    snapshot.temperatureC?.let { this[WidgetKeys.TEMP_C] = it }
                }
            }
            ChargeMeterWidget().update(context, id)
        }
    }
}
