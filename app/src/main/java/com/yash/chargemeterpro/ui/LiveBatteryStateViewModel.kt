package com.yash.chargemeterpro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.chargemeterpro.data.local.SettingsDataStore
import com.yash.chargemeterpro.data.repository.BatteryRepository
import com.yash.chargemeterpro.data.repository.ChargingSessionRepository
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.usecase.ChargeTimeEstimator
import com.yash.chargemeterpro.domain.usecase.ChargerAnalysis
import com.yash.chargemeterpro.domain.usecase.ChargerAnalyzer
import com.yash.chargemeterpro.domain.usecase.ChargingSpeed
import com.yash.chargemeterpro.domain.usecase.ChargingSpeedClassifier
import com.yash.chargemeterpro.domain.usecase.PowerCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single shared source of "what's happening with the battery right now"
 * state, consumed by HomeViewModel and LiveMonitorViewModel via
 * composition rather than each re-implementing the same broadcast
 * collection + rolling-window bookkeeping independently. This ViewModel
 * itself does NOT write to the database — that's ChargingMonitorService's
 * job when Always-On is enabled, or LiveMonitorViewModel's own explicit
 * session-recording calls when the app is foregrounded without the
 * service running (see LiveMonitorViewModel).
 */
@HiltViewModel
class LiveBatteryStateViewModel @Inject constructor(
    private val batteryRepository: BatteryRepository,
    private val chargerAnalyzer: ChargerAnalyzer,
    private val sessionRepository: ChargingSessionRepository,
    settingsDataStore: SettingsDataStore
) : ViewModel() {

    /** Rolling window of (timestamp, percent) pairs for ChargeTimeEstimator — capped so it doesn't grow unbounded during a long session. */
    private val rateWindow = ArrayDeque<ChargeTimeEstimator.RateSample>()
    private val maxWindowSize = 300 // ~10 minutes at a 2s cadence, generous headroom

    private val _snapshot = MutableStateFlow<BatterySnapshot?>(null)
    val snapshot: StateFlow<BatterySnapshot?> = _snapshot.asStateFlow()

    private val _timeEstimate = MutableStateFlow(
        ChargeTimeEstimator.TimeEstimate(null, null, null, null, null)
    )
    val timeEstimate: StateFlow<ChargeTimeEstimator.TimeEstimate> = _timeEstimate.asStateFlow()

    private val _chargerAnalysis = MutableStateFlow<ChargerAnalysis?>(null)
    val chargerAnalysis: StateFlow<ChargerAnalysis?> = _chargerAnalysis.asStateFlow()

    private val _chargingSpeed = MutableStateFlow(ChargingSpeed.UNKNOWN)
    val chargingSpeed: StateFlow<ChargingSpeed> = _chargingSpeed.asStateFlow()

    val activeSessionId: StateFlow<Long?> = sessionRepository.observeActiveSession()
        .distinctUntilChanged()
        .let { flow ->
            kotlinx.coroutines.flow.MutableStateFlow<Long?>(null).also { out ->
                viewModelScope.launch {
                    flow.collect { session -> out.value = session?.id }
                }
            }
        }

    val screenOnStatsEnabled: StateFlow<Boolean> = settingsDataStore.screenOnStatsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        viewModelScope.launch {
            batteryRepository.observeSnapshots().collect { snap ->
                onNewSnapshot(snap)
            }
        }
    }

    private fun onNewSnapshot(snap: BatterySnapshot) {
        _snapshot.value = snap
        _chargerAnalysis.value = chargerAnalyzer.analyze(snap)
        _chargingSpeed.value = ChargingSpeedClassifier.classify(snap)

        if (snap.isCharging) {
            rateWindow.addLast(ChargeTimeEstimator.RateSample(snap.timestampMillis, snap.batteryPercent))
            while (rateWindow.size > maxWindowSize) rateWindow.removeFirst()
            _timeEstimate.value = ChargeTimeEstimator.estimate(snap, rateWindow.toList())
        } else {
            rateWindow.clear()
            _timeEstimate.value = ChargeTimeEstimator.TimeEstimate(null, null, null, null, null)
        }
    }

    fun currentPowerWatts(): Double? = _snapshot.value?.let { PowerCalculator.batteryInputPowerWatts(it) }
}
