package com.yash.chargemeterpro.ui.screens.livemonitor

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.chargemeterpro.data.repository.BatteryRepository
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.usecase.ChargerAnalysis
import com.yash.chargemeterpro.domain.usecase.ChargerAnalyzer
import com.yash.chargemeterpro.domain.usecase.PowerCalculator
import com.yash.chargemeterpro.service.ChargingMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GraphMetric { WATTAGE, CURRENT, VOLTAGE, BATTERY_PERCENT, TEMPERATURE }

data class GraphPoint(val timestampMillis: Long, val value: Float)

data class LiveMonitorUiState(
    val snapshot: BatterySnapshot? = null,
    val chargerAnalysis: ChargerAnalysis? = null,
    val selectedMetric: GraphMetric = GraphMetric.WATTAGE,
    val graphPoints: Map<GraphMetric, List<GraphPoint>> = emptyMap()
)

/**
 * Drives the Live Monitor screen: keeps a capped in-memory ring buffer per
 * graph metric (feature #4, "Live Charging Graphs" — switchable between
 * Wattage/Current/Voltage/Battery%/Temperature vs Time) for the live
 * ring/sparkline visuals only.
 *
 * Session recording (start/sample/end) is deliberately NOT owned here
 * anymore. It used to be: this ViewModel would start+end its own "local"
 * session whenever charging was detected while this screen happened to be
 * open. That seemed harmless because ChargingSessionRepository.startSession
 * is idempotent, but ending a session correctly needs somebody to still be
 * around and watching at the moment the charger is unplugged — and this
 * ViewModel is destroyed the instant the user leaves this screen,
 * backgrounds the app, or closes it entirely. In practice that left
 * sessions stuck with a null end time/percent/voltage in History forever
 * ("Charging" shown indefinitely, exit stats never recorded) any time the
 * user didn't happen to still be sitting on this exact screen at unplug
 * time — which is the common case, not the edge case.
 *
 * Instead, opening this screen while charging is a strong signal to make
 * sure the durable, foreground ChargingMonitorService is running — that
 * service is the single owner of session start/sample/end, survives the
 * screen closing, the app backgrounding, and the app being swiped away
 * entirely, and (via PowerConnectionReceiver) is also nudged awake right
 * at unplug time specifically to close out the session accurately. Both
 * this screen and the service still read from the same live snapshot flow
 * for the on-screen numbers, so what's displayed here matches what's
 * ultimately persisted.
 */
@HiltViewModel
class LiveMonitorViewModel @Inject constructor(
    private val batteryRepository: BatteryRepository,
    private val chargerAnalyzer: ChargerAnalyzer,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val maxPointsPerMetric = 240 // ~8 minutes at 2s cadence — enough for a readable live graph without unbounded memory growth

    private val buffers: MutableMap<GraphMetric, ArrayDeque<GraphPoint>> =
        GraphMetric.entries.associateWith { ArrayDeque<GraphPoint>() }.toMutableMap()

    private val _uiState = MutableStateFlow(LiveMonitorUiState())
    val uiState: StateFlow<LiveMonitorUiState> = _uiState.asStateFlow()

    // Tracks whether we've already asked the service to start for the
    // charging session currently in progress, so we don't call
    // startForegroundService() on every single snapshot tick — only once
    // per charging session (reset back to false as soon as we observe
    // charging has stopped).
    private var serviceNudgedForCurrentSession = false

    init {
        viewModelScope.launch {
            batteryRepository.observeSnapshots().collect { snap ->
                handleSnapshot(snap)
            }
        }
    }

    fun selectMetric(metric: GraphMetric) {
        _uiState.value = _uiState.value.copy(selectedMetric = metric)
    }

    private suspend fun handleSnapshot(snap: BatterySnapshot) {
        val analysis = chargerAnalyzer.analyze(snap)
        val powerWatts = PowerCalculator.batteryInputPowerWatts(snap)

        appendPoint(GraphMetric.WATTAGE, snap.timestampMillis, powerWatts?.toFloat())
        appendPoint(GraphMetric.CURRENT, snap.timestampMillis, snap.currentMilliAmpsNormalized?.toFloat())
        appendPoint(GraphMetric.VOLTAGE, snap.timestampMillis, snap.voltageVolts?.toFloat())
        appendPoint(GraphMetric.BATTERY_PERCENT, snap.timestampMillis, snap.batteryPercent.toFloat())
        appendPoint(GraphMetric.TEMPERATURE, snap.timestampMillis, snap.temperatureC?.toFloat())

        ensureServiceRunningIfCharging(snap)

        _uiState.value = _uiState.value.copy(
            snapshot = snap,
            chargerAnalysis = analysis,
            graphPoints = buffers.mapValues { it.value.toList() }
        )
    }

    private fun appendPoint(metric: GraphMetric, timestamp: Long, value: Float?) {
        if (value == null) return
        val buffer = buffers.getValue(metric)
        buffer.addLast(GraphPoint(timestamp, value))
        while (buffer.size > maxPointsPerMetric) buffer.removeFirst()
    }

    /**
     * Makes sure ChargingMonitorService is alive whenever this screen
     * observes charging in progress, instead of this ViewModel recording
     * a session it can't reliably finish. startForegroundService() is
     * cheap and safe to call redundantly (Service#onStartCommand just
     * restarts the poll loop if it's already running), but we still only
     * call it once per session start — via serviceNudgedForCurrentSession
     * — rather than on every ~2s tick, purely to avoid spamming the
     * platform call for no benefit.
     *
     * The service itself decides (via its own autoStartMonitoring /
     * alwaysOnMonitorEnabled checks) whether to actually create a session
     * row; this call is just "wake up and look," matching what
     * PowerConnectionReceiver already does on ACTION_POWER_CONNECTED —
     * this is the same nudge, just also covering the case where the user
     * opens Live Monitor mid-charge (e.g. after a reboot, or if the
     * connect broadcast was missed) rather than only at plug-in time.
     */
    private fun ensureServiceRunningIfCharging(snap: BatterySnapshot) {
        if (snap.isCharging) {
            if (!serviceNudgedForCurrentSession) {
                serviceNudgedForCurrentSession = true
                try {
                    ContextCompat.startForegroundService(
                        appContext,
                        Intent(appContext, ChargingMonitorService::class.java)
                    )
                } catch (e: IllegalStateException) {
                    // Foreground-service start restriction rejected the
                    // call (rare from an active foreground screen, since
                    // the app itself being in the foreground is normally
                    // an exemption) — fail safe rather than crash the UI.
                    android.util.Log.w(
                        "LiveMonitorViewModel",
                        "Could not ensure ChargingMonitorService is running: ${e.message}"
                    )
                }
            }
        } else {
            serviceNudgedForCurrentSession = false
        }
    }

    fun clearGraphBuffers() {
        buffers.values.forEach { it.clear() }
        _uiState.value = _uiState.value.copy(graphPoints = emptyMap())
    }
}
