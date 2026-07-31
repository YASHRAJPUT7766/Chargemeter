package com.yash.chargemeterpro.ui.screens.batteryhealth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.chargemeterpro.data.local.SettingsDataStore
import com.yash.chargemeterpro.data.repository.BatteryRepository
import com.yash.chargemeterpro.domain.model.AvailableOr
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.usecase.BatteryCapacityEstimator
import com.yash.chargemeterpro.domain.usecase.BatteryHealthScorer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BatteryHealthUiState(
    val snapshot: BatterySnapshot? = null,
    val healthScoreResult: BatteryHealthScorer.HealthScoreResult? = null,
    val deviceSkinTempC: AvailableOr<Float> = AvailableOr.Unavailable
)

@HiltViewModel
class BatteryHealthViewModel @Inject constructor(
    private val batteryRepository: BatteryRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatteryHealthUiState())
    val uiState: StateFlow<BatteryHealthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            batteryRepository.observeSnapshots().collect { snap ->
                onSnapshot(snap)
            }
        }
    }

    private suspend fun onSnapshot(snap: BatterySnapshot) {
        // Establish the capacity baseline the FIRST time we ever see a
        // usable charge-counter reading on this device — subsequent
        // readings are compared against this fixed point to estimate
        // fade over time. setCapacityBaselineIfAbsent is a no-op once a
        // baseline already exists, so this is safe to call on every
        // snapshot without ever overwriting the original baseline.
        val impliedCapacity = BatteryCapacityEstimator.impliedFullCapacityMicroAh(snap)
        (impliedCapacity as? AvailableOr.Value)?.value?.let { capacity ->
            settingsDataStore.setCapacityBaselineIfAbsent(capacity)
        }

        val baseline = settingsDataStore.capacityBaselineMicroAh.first()
        val fadePercent = BatteryCapacityEstimator.capacityFadePercent(impliedCapacity, baseline)
        val healthScore = BatteryHealthScorer.score(snap, fadePercent)

        val skinTemp = batteryRepository.readDeviceSkinTemperature()

        _uiState.value = BatteryHealthUiState(
            snapshot = snap,
            healthScoreResult = healthScore,
            deviceSkinTempC = skinTemp
        )
    }

    fun resetCapacityBaseline() {
        viewModelScope.launch { settingsDataStore.resetCapacityBaseline() }
    }
}
