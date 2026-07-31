package com.yash.chargemeterpro.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.chargemeterpro.data.repository.ChargingSessionRepository
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.model.ChargingStatus
import com.yash.chargemeterpro.domain.usecase.ChargeTimeEstimator
import com.yash.chargemeterpro.domain.usecase.ChargingSpeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val snapshot: BatterySnapshot? = null,
    val powerWatts: Double? = null,
    val chargingSpeed: ChargingSpeed = ChargingSpeed.UNKNOWN,
    val timeEstimate: ChargeTimeEstimator.TimeEstimate = ChargeTimeEstimator.TimeEstimate(null, null, null, null, null),
    val recentSessionCount: Int = 0
)

/**
 * HomeViewModel doesn't re-implement battery collection — it's a thin
 * presentation wrapper that would normally compose LiveBatteryStateViewModel's
 * flows. Because Hilt ViewModels can't easily inject another ViewModel
 * directly, HomeScreen instead obtains BOTH viewmodels via hiltViewModel()
 * and combines their state at the Compose call site (see HomeScreen.kt) —
 * this ViewModel is kept focused on the small amount of Home-specific
 * state that doesn't belong in the shared live-state model, namely the
 * completed-session count shown in the "today" strip.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    sessionRepository: ChargingSessionRepository
) : ViewModel() {

    val completedSessionCount: StateFlow<Int> = sessionRepository.observeCompletedSessionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}

/** Pure formatting helpers kept outside the ViewModel/Composable so they're trivially unit-testable. */
object HomeFormatters {
    fun statusLabel(status: ChargingStatus): String = when (status) {
        ChargingStatus.CHARGING -> "CHARGING"
        ChargingStatus.DISCHARGING -> "DISCHARGING"
        ChargingStatus.NOT_CHARGING -> "NOT CHARGING"
        ChargingStatus.FULL -> "FULL"
        ChargingStatus.UNKNOWN -> "UNKNOWN"
    }

    fun speedLabel(speed: ChargingSpeed): String = when (speed) {
        ChargingSpeed.FAST -> "FAST"
        ChargingSpeed.NORMAL -> "NORMAL"
        ChargingSpeed.SLOW -> "SLOW"
        ChargingSpeed.TRICKLE -> "TRICKLE"
        ChargingSpeed.UNKNOWN -> "—"
    }

    fun minutesToReadable(minutes: Long?): String {
        if (minutes == null) return "Calculating…"
        if (minutes <= 0) return "Almost done"
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}min" else "${m} min"
    }

    fun wattsText(watts: Double?): String = watts?.let { "%.2f".format(it) } ?: "—"
    fun voltsText(volts: Double?): String = volts?.let { "%.2f".format(it) } ?: "—"
    fun mAText(mA: Double?): String = mA?.let { "%.0f".format(it) } ?: "—"
    fun tempText(c: Double?): String = c?.let { "%.1f".format(it) } ?: "—"
}
