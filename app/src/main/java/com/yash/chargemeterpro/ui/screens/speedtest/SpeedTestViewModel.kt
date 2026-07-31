package com.yash.chargemeterpro.ui.screens.speedtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.chargemeterpro.data.battery.ChargingPollScheduler
import com.yash.chargemeterpro.data.repository.BatteryRepository
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.usecase.PowerCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SpeedTestPhase { IDLE, RUNNING, COMPLETED }

data class SpeedTestSample(val elapsedSeconds: Long, val powerWatts: Double?, val batteryPercent: Int)

data class SpeedTestReport(
    val durationSeconds: Long,
    val startBatteryPercent: Int,
    val endBatteryPercent: Int,
    val percentGained: Int,
    val averagePowerWatts: Double?,
    val maxPowerWatts: Double?,
    val samples: List<SpeedTestSample>
)

data class SpeedTestUiState(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val selectedDurationSeconds: Long = 300L, // default 5 minutes
    val elapsedSeconds: Long = 0L,
    val liveSamples: List<SpeedTestSample> = emptyList(),
    val currentPowerWatts: Double? = null,
    val report: SpeedTestReport? = null,
    val notCurrentlyCharging: Boolean = false
)

/**
 * Feature #9, Charging Speed Test: records battery state at a tight
 * ~1s cadence (see ChargingPollScheduler.SPEED_TEST_INTERVAL_MS) for a
 * user-selected bounded duration, independent of the normal charging
 * session tracker — a speed test is a deliberate, short diagnostic run,
 * not part of the person's regular charging history, so its samples are
 * kept in-memory only and summarized into a [SpeedTestReport] rather than
 * written to the sessions table. If the user wants to keep a record of a
 * test, ShareableReportBuilder (see export/) can turn the finished report
 * into a shareable text/CSV summary from this in-memory state.
 */
@HiltViewModel
class SpeedTestViewModel @Inject constructor(
    private val batteryRepository: BatteryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpeedTestUiState())
    val uiState: StateFlow<SpeedTestUiState> = _uiState.asStateFlow()

    private var testJob: Job? = null
    private var startBatteryPercent: Int = 0

    fun selectDuration(seconds: Long) {
        if (_uiState.value.phase == SpeedTestPhase.RUNNING) return
        _uiState.value = _uiState.value.copy(selectedDurationSeconds = seconds)
    }

    fun startTest() {
        val initial = batteryRepository.readSnapshotNow()
        if (!initial.isCharging) {
            _uiState.value = _uiState.value.copy(notCurrentlyCharging = true)
            return
        }

        startBatteryPercent = initial.batteryPercent
        _uiState.value = _uiState.value.copy(
            phase = SpeedTestPhase.RUNNING,
            elapsedSeconds = 0L,
            liveSamples = emptyList(),
            report = null,
            notCurrentlyCharging = false
        )

        testJob?.cancel()
        testJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val durationMillis = _uiState.value.selectedDurationSeconds * 1000L

            while (true) {
                val elapsedMillis = System.currentTimeMillis() - startTime
                if (elapsedMillis >= durationMillis) break

                val snap = batteryRepository.readSnapshotNow()
                if (!snap.isCharging) {
                    // Charger was disconnected mid-test — stop early and
                    // report what we have rather than silently continuing
                    // to "test" a state that no longer reflects charging.
                    break
                }

                recordSample(snap, elapsedMillis / 1000)
                delay(ChargingPollScheduler.SPEED_TEST_INTERVAL_MS)
            }

            finishTest()
        }
    }

    private fun recordSample(snap: BatterySnapshot, elapsedSeconds: Long) {
        val powerWatts = PowerCalculator.batteryInputPowerWatts(snap)
        val sample = SpeedTestSample(elapsedSeconds, powerWatts, snap.batteryPercent)
        val updatedSamples = _uiState.value.liveSamples + sample
        _uiState.value = _uiState.value.copy(
            elapsedSeconds = elapsedSeconds,
            liveSamples = updatedSamples,
            currentPowerWatts = powerWatts
        )
    }

    private fun finishTest() {
        val samples = _uiState.value.liveSamples
        val finalSnapshot = batteryRepository.readSnapshotNow()
        val powers = samples.mapNotNull { it.powerWatts }

        val report = SpeedTestReport(
            durationSeconds = _uiState.value.elapsedSeconds,
            startBatteryPercent = startBatteryPercent,
            endBatteryPercent = finalSnapshot.batteryPercent,
            percentGained = (finalSnapshot.batteryPercent - startBatteryPercent).coerceAtLeast(0),
            averagePowerWatts = powers.takeIf { it.isNotEmpty() }?.average(),
            maxPowerWatts = powers.maxOrNull(),
            samples = samples
        )

        _uiState.value = _uiState.value.copy(phase = SpeedTestPhase.COMPLETED, report = report)
    }

    fun cancelTest() {
        testJob?.cancel()
        _uiState.value = _uiState.value.copy(phase = SpeedTestPhase.IDLE, elapsedSeconds = 0L, liveSamples = emptyList())
    }

    fun resetTest() {
        _uiState.value = SpeedTestUiState(selectedDurationSeconds = _uiState.value.selectedDurationSeconds)
    }

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
    }
}
