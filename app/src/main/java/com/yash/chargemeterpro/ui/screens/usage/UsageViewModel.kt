package com.yash.chargemeterpro.ui.screens.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.chargemeterpro.data.usage.DailyUsageSummary
import com.yash.chargemeterpro.data.usage.UsagePermissionState
import com.yash.chargemeterpro.data.usage.UsageStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class UsageUiState(
    val selectedEpochDay: Long = LocalDate.now().toEpochDay(),
    val earliestEpochDay: Long = LocalDate.now().toEpochDay() - (UsageStatsRepository.MAX_HISTORY_DAYS - 1),
    val permissionState: UsagePermissionState = UsagePermissionState.UNKNOWN,
    val summary: DailyUsageSummary? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class UsageViewModel @Inject constructor(
    private val repository: UsageStatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsageUiState())
    val uiState: StateFlow<UsageUiState> = _uiState.asStateFlow()

    val today: Long = LocalDate.now().toEpochDay()

    init {
        refreshPermissionAndLoad()
    }

    /** Call from onResume — the user may have just come back from the system Usage Access settings screen. */
    fun refreshPermissionAndLoad() {
        val granted = repository.hasUsageAccess()
        _uiState.value = _uiState.value.copy(
            permissionState = if (granted) UsagePermissionState.GRANTED else UsagePermissionState.NOT_GRANTED,
            earliestEpochDay = repository.earliestAvailableEpochDay()
        )
        if (granted) loadDay(_uiState.value.selectedEpochDay)
    }

    fun selectDay(epochDay: Long) {
        val clamped = epochDay.coerceIn(_uiState.value.earliestEpochDay, today)
        _uiState.value = _uiState.value.copy(selectedEpochDay = clamped)
        if (_uiState.value.permissionState == UsagePermissionState.GRANTED) loadDay(clamped)
    }

    fun goToPreviousDay() = selectDay(_uiState.value.selectedEpochDay - 1)
    fun goToNextDay() = selectDay(_uiState.value.selectedEpochDay + 1)
    fun canGoNext(): Boolean = _uiState.value.selectedEpochDay < today
    fun canGoPrevious(): Boolean = _uiState.value.selectedEpochDay > _uiState.value.earliestEpochDay

    /**
     * Re-queries the currently selected day's summary, but only when
     * that day is today — a past day's totals are already final and
     * re-querying them is pure wasted work (a UsageStatsManager call is
     * not free). Meant to be called periodically (see UsageScreen's
     * LaunchedEffect polling loop) so the donut and per-app list keep
     * advancing in real time while the user is looking at today and the
     * screen stays open, instead of freezing at whatever the total was
     * at the moment the screen first loaded.
     *
     * Deliberately skips the isLoading flip that loadDay() does for the
     * user-initiated path — a silent background refresh shouldn't flash
     * a spinner over data that's already on screen and still valid.
     */
    fun refreshIfToday() {
        val state = _uiState.value
        if (state.permissionState != UsagePermissionState.GRANTED) return
        if (state.selectedEpochDay != today) return
        viewModelScope.launch {
            val summary = repository.getDailySummary(today)
            if (_uiState.value.selectedEpochDay == today) {
                _uiState.value = _uiState.value.copy(summary = summary)
            }
        }
    }

    private fun loadDay(epochDay: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val summary = repository.getDailySummary(epochDay)
            // Guard against a stale response landing after the user has
            // already swiped further (fast repeated swipes racing this
            // coroutine) — only apply if still the selected day.
            if (_uiState.value.selectedEpochDay == epochDay) {
                _uiState.value = _uiState.value.copy(summary = summary, isLoading = false)
            }
        }
    }
}
