package com.yash.chargemeterpro.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
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
import androidx.glance.layout.Alignment
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
import com.yash.chargemeterpro.domain.usecase.PowerCalculator

/**
 * Second home-screen widget (feature #12 follow-up): rather than repeating
 * the instrument-panel readout ChargeMeterWidget already shows, this one
 * answers the single question people actually open a battery app for —
 * "how much longer?" — plus a plain-language read on whether the current
 * charge is fast, normal, or slow.
 *
 * Data is pushed from the exact same place as ChargeMeterWidget
 * (ChargingMonitorService's poll loop via [TimeRemainingWidgetUpdater]),
 * using this app's own existing ChargeTimeEstimator / DischargeTimeEstimator
 * — never a fabricated number. If neither estimator has enough trailing
 * samples yet, this widget shows "—" rather than guessing.
 */
class TimeRemainingWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            TimeRemainingContent()
        }
    }
}

private val BgColor = Color(0xFF0A0E12)
private val PhosphorGreenColor = Color(0xFF39FF88)
private val PanelGrayColor = Color(0xFF8B98A5)
private val WarningAmberColor = Color(0xFFFF6B4A)
private val VoltageBlueColor = Color(0xFF5B8DEF)

/** Coarse, human-readable charging-speed bucket derived from measured watts — no device-specific "rated wattage" table exists to compare against, so this is deliberately relative rather than claiming to know a phone's max input. */
private enum class ChargeSpeedBucket(val label: String, val color: Color) {
    FAST("FAST", PhosphorGreenColor),
    NORMAL("NORMAL", VoltageBlueColor),
    SLOW("SLOW", WarningAmberColor),
    UNKNOWN("—", PanelGrayColor)
}

private fun speedBucketFor(watts: Double?): ChargeSpeedBucket = when {
    watts == null -> ChargeSpeedBucket.UNKNOWN
    watts >= 10.0 -> ChargeSpeedBucket.FAST
    watts >= 4.0 -> ChargeSpeedBucket.NORMAL
    else -> ChargeSpeedBucket.SLOW
}

private fun formatMinutes(totalMinutes: Long?): String {
    if (totalMinutes == null) return "—"
    if (totalMinutes < 1) return "<1m"
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun TimeRemainingContent() {
    val prefs = currentState<Preferences>()
    val isCharging = prefs[TimeRemainingWidgetKeys.IS_CHARGING] ?: false
    val isFull = prefs[TimeRemainingWidgetKeys.IS_FULL] ?: false
    val minutesRemaining = prefs[TimeRemainingWidgetKeys.MINUTES_REMAINING]
    val wattsW = prefs[TimeRemainingWidgetKeys.WATTS_W]

    val headline = when {
        isFull -> "Full"
        isCharging -> formatMinutes(minutesRemaining)
        else -> formatMinutes(minutesRemaining)
    }
    val sublabel = when {
        isFull -> "Battery charged"
        isCharging -> "until full"
        minutesRemaining != null -> "backup left"
        else -> "estimating…"
    }
    val speed = speedBucketFor(wattsW)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(BgColor))
            .padding(12.dp)
            .clickable(actionStartActivity(android.content.Intent(LocalContext.current, MainActivity::class.java)))
    ) {
        Text(
            text = headline,
            style = TextStyle(color = ColorProvider(PhosphorGreenColor), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        )
        Text(
            text = sublabel,
            style = TextStyle(color = ColorProvider(PanelGrayColor), fontSize = 11.sp)
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = if (isCharging) "⚡ ${speed.label}" else "",
                style = TextStyle(color = ColorProvider(speed.color), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

object TimeRemainingWidgetKeys {
    val IS_CHARGING = booleanPreferencesKey("tr_is_charging")
    val IS_FULL = booleanPreferencesKey("tr_is_full")
    val MINUTES_REMAINING = longPreferencesKey("tr_minutes_remaining")
    val WATTS_W = doublePreferencesKey("tr_watts_w")
    val STATUS = stringPreferencesKey("tr_status")
}

class TimeRemainingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimeRemainingWidget()
}

/**
 * Pushes charge/discharge time estimates into every placed instance of
 * this widget. Called from ChargingMonitorService right alongside
 * ChargeMeterWidgetUpdater.pushUpdate — same cadence, same call site —
 * so both widgets always reflect the same poll.
 */
object TimeRemainingWidgetUpdater {

    suspend fun pushUpdate(
        context: Context,
        isCharging: Boolean,
        isFull: Boolean,
        minutesRemaining: Long?,
        voltageVolts: Double?,
        currentAmps: Double?
    ) {
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(TimeRemainingWidget::class.java)
        if (glanceIds.isEmpty()) return

        val watts = PowerCalculator.batteryInputPowerWatts(voltageVolts, currentAmps)

        glanceIds.forEach { id ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[TimeRemainingWidgetKeys.IS_CHARGING] = isCharging
                    this[TimeRemainingWidgetKeys.IS_FULL] = isFull
                    if (minutesRemaining != null) {
                        this[TimeRemainingWidgetKeys.MINUTES_REMAINING] = minutesRemaining
                    } else {
                        this.remove(TimeRemainingWidgetKeys.MINUTES_REMAINING)
                    }
                    watts?.let { this[TimeRemainingWidgetKeys.WATTS_W] = it }
                }
            }
            TimeRemainingWidget().update(context, id)
        }
    }
}
