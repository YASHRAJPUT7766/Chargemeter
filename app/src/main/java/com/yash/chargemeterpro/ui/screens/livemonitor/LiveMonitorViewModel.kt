package com.yash.chargemeterpro.ui.screens.livemonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.chargemeterpro.data.repository.BatteryRepository
import com.yash.chargemeterpro.data.repository.ChargingSessionRepository
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.usecase.ChargerAnalysis
import com.yash.chargemeterpro.domain.usecase.ChargerAnalyzer
import com.yash.chargemeterpro.domain.usecase.PowerCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val graphPoints: Map<GraphMetric, List<GraphPoint>> = emptyMap(),
    val isRecordingLocally: Boolean = false
)

/**
 * Drives the Live Monitor screen: keeps a capped in-memory ring buffer per
 * graph metric (feature #4, "Live Charging Graphs" — switchable between
 * Wattage/Current/Voltage/Battery%/Temperature vs Time), and, if the
 * Always-On foreground service is NOT currently running a session, also
 * takes on lightweight local session recording itself so charging
 * history/graphs still populate correctly for users who haven't enabled
 * that background service — matching the spec's "Whenever charging
 * starts, automatically create a charging session" requirement
 * regardless of which recording path is active. Both paths write through
 * the same ChargingSessionRepository, so History/Statistics can't tell
 * (and don't need to care) which one produced a given session.
 */
@HiltViewModel
class LiveMonitorViewModel @Inject constructor(
    private val batteryRepository: BatteryRepository,
    private val chargerAnalyzer: ChargerAnalyzer,
    private val sessionRepository: ChargingSessionRepository
) : ViewModel() {

    private val maxPointsPerMetric = 240 // ~8 minutes at 2s cadence — enough for a readable live graph without unbounded memory growth

    private val buffers: MutableMap<GraphMetric, ArrayDeque<GraphPoint>> =
        GraphMetric.entries.associateWith { ArrayDeque<GraphPoint>() }.toMutableMap()

    private val _uiState = MutableStateFlow(LiveMonitorUiState())
    val uiState: StateFlow<LiveMonitorUiState> = _uiState.asStateFlow()

    private var localSessionId: Long? = null

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

        maybeRecordLocalSession(snap)

        _uiState.value = _uiState.value.copy(
            snapshot = snap,
            chargerAnalysis = analysis,
            graphPoints = buffers.mapValues { it.value.toList() },
            isRecordingLocally = localSessionId != null
        )
    }

    private fun appendPoint(metric: GraphMetric, timestamp: Long, value: Float?) {
        if (value == null) return
        val buffer = buffers.getValue(metric)
        buffer.addLast(GraphPoint(timestamp, value))
        while (buffer.size > maxPointsPerMetric) buffer.removeFirst()
    }

    /**
     * Records a local session whenever charging is happening and this
     * ViewModel doesn't already believe it owns one. We deliberately do
     * NOT pre-query the DB for an existing active session on every tick
     * (that would mean a suspend DB read every 2s) — instead we rely on
     * ChargingSessionRepository.startSession()'s own guard: if the
     * foreground service already created an active session, startSession
     * returns that existing session's id rather than inserting a
     * duplicate, so calling it here is always safe and idempotent even
     * if both this ViewModel and the service are active at once.
     *
     * Sample WRITES are a separate concern from session creation, though:
     * if the Always-On service is also running (same active session,
     * different poll loop at its own ~15s cadence), both this
     * ViewModel's ~2s ticks and the service's ticks would otherwise both
     * call recordSample() for the same session, producing denser-than-
     * intended and partially redundant rows. We guard against that with
     * a minimum spacing check — only write a sample if enough time has
     * passed since the last one we wrote — which naturally converges to
     * "whichever caller writes more often wins" without needing the two
     * components to coordinate directly.
     */
    private var lastSampleWriteMillis = 0L
    private val minSampleSpacingMillis = 1_500L // slightly under our own 2s foreground cadence

    private suspend fun maybeRecordLocalSession(snap: BatterySnapshot) {
        when {
            snap.isCharging && localSessionId == null -> {
                localSessionId = sessionRepository.startSession(snap)
                lastSampleWriteMillis = snap.timestampMillis
            }
            snap.isCharging && localSessionId != null -> {
                if (snap.timestampMillis - lastSampleWriteMillis >= minSampleSpacingMillis) {
                    sessionRepository.recordSample(localSessionId!!, snap)
                    lastSampleWriteMillis = snap.timestampMillis
                }
            }
            !snap.isCharging && localSessionId != null -> {
                sessionRepository.endSession(localSessionId!!, snap, completedNormally = true)
                localSessionId = null
            }
        }
    }

    fun clearGraphBuffers() {
        buffers.values.forEach { it.clear() }
        _uiState.value = _uiState.value.copy(graphPoints = emptyMap())
    }
}
