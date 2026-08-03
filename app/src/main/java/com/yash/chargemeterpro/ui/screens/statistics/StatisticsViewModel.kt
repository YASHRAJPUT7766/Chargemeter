package com.yash.chargemeterpro.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.chargemeterpro.data.local.dao.DrainSampleDao
import com.yash.chargemeterpro.data.repository.ChargingSessionRepository
import com.yash.chargemeterpro.domain.usecase.DrainRateCalculator
import com.yash.chargemeterpro.domain.usecase.DrainRateResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class StatisticsUiState(
    val todaySessionCount: Int = 0,
    val todayTotalChargingMinutes: Long = 0,
    val todayAveragePowerWatts: Double? = null,
    val allTimeAveragePowerWatts: Double? = null,
    val allTimeMaxPowerWatts: Double? = null,
    val allTimeTotalEnergyWh: Double? = null,
    val allTimeSessionCount: Int = 0,
    val averageSessionDurationMinutes: Long? = null,
    val drainRate: DrainRateResult = DrainRateResult(null, 0, 0.0),
    val batteryPercentHistory: List<Float> = emptyList()
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val sessionRepository: ChargingSessionRepository,
    private val drainSampleDao: DrainSampleDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val (startOfDay, endOfDay) = todayBoundsMillis()

            combine(
                sessionRepository.observeSessionsForDay(startOfDay, endOfDay),
                sessionRepository.observeAllSessions(),
                sessionRepository.observeOverallAveragePowerWatts(),
                sessionRepository.observeTotalEnergyWattHours()
            ) { todaySessions, allSessions, avgPower, totalEnergy ->
                val completedToday = todaySessions.filter { it.endTimeMillis != null }
                val todayMinutes = completedToday.sumOf { s ->
                    ((s.endTimeMillis ?: s.startTimeMillis) - s.startTimeMillis) / 60_000L
                }
                val todayAvgPower = completedToday.mapNotNull { it.averagePowerWatts }
                    .takeIf { it.isNotEmpty() }?.average()

                val completedAll = allSessions.filter { it.endTimeMillis != null }
                val maxPowerAll = completedAll.mapNotNull { it.maxPowerWatts }.maxOrNull()
                val avgDurationMinutes = completedAll.takeIf { it.isNotEmpty() }?.let { list ->
                    list.sumOf { s -> ((s.endTimeMillis ?: s.startTimeMillis) - s.startTimeMillis) / 60_000L } / list.size
                }

                StatisticsUiState(
                    todaySessionCount = todaySessions.size,
                    todayTotalChargingMinutes = todayMinutes,
                    todayAveragePowerWatts = todayAvgPower,
                    allTimeAveragePowerWatts = avgPower,
                    allTimeMaxPowerWatts = maxPowerAll,
                    allTimeTotalEnergyWh = totalEnergy,
                    allTimeSessionCount = completedAll.size,
                    averageSessionDurationMinutes = avgDurationMinutes,
                    drainRate = _uiState.value.drainRate
                )
            }.collect { partialState ->
                // Preserve whatever drain rate was computed by the second
                // init block below — these two data sources update on
                // independent cadences (session flows are reactive,
                // drain rate is a one-shot read), so we merge rather than
                // let one overwrite the other's contribution.
                _uiState.value = partialState.copy(drainRate = _uiState.value.drainRate)
            }
        }

        viewModelScope.launch {
            val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            val samples = drainSampleDao.getSince(sevenDaysAgo)
            val rate = DrainRateCalculator.calculate(samples)
            _uiState.value = _uiState.value.copy(
                drainRate = rate,
                batteryPercentHistory = samples.map { it.batteryPercent.toFloat() }
            )
        }
    }

    private fun todayBoundsMillis(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis
        return start to end
    }
}
