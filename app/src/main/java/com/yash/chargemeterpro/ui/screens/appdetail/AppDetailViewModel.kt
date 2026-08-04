package com.yash.chargemeterpro.ui.screens.appdetail

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.chargemeterpro.data.usage.AppLimitsDataStore
import com.yash.chargemeterpro.data.usage.AppUsageInfo
import com.yash.chargemeterpro.data.usage.UsageHistoryPoint
import com.yash.chargemeterpro.data.usage.UsageStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class AppDetailPeriod(val label: String, val days: Int) {
    TODAY("Today", 1),
    THREE_DAYS("3 Days", 3),
    SEVEN_DAYS("7 Days", 7),
    AVAILABLE("Available", UsageStatsRepository.MAX_HISTORY_DAYS)
}

data class AppDetailUiState(
    val packageName: String = "",
    val appName: String = "",
    val icon: Drawable? = null,
    val todayUsage: AppUsageInfo? = null,
    val selectedPeriod: AppDetailPeriod = AppDetailPeriod.SEVEN_DAYS,
    val history: List<UsageHistoryPoint> = emptyList(),
    val limitMinutes: Int? = null,
    val limitEnabled: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val repository: UsageStatsRepository,
    private val limitsDataStore: AppLimitsDataStore,
    @ApplicationContext private val appContext: android.content.Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val packageName: String = checkNotNull(savedStateHandle["packageName"])

    private val _uiState = MutableStateFlow(AppDetailUiState(packageName = packageName))
    val uiState: StateFlow<AppDetailUiState> = _uiState.asStateFlow()

    init {
        loadAppMeta()
        viewModelScope.launch {
            val limit = limitsDataStore.limits.first()[packageName]
            _uiState.value = _uiState.value.copy(
                limitMinutes = limit?.dailyLimitMinutes,
                limitEnabled = limit?.enabled ?: false
            )
        }
        selectPeriod(AppDetailPeriod.SEVEN_DAYS)
    }

    private fun loadAppMeta() {
        try {
            val pm = appContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            _uiState.value = _uiState.value.copy(
                appName = pm.getApplicationLabel(info).toString(),
                icon = try { pm.getApplicationIcon(info) } catch (_: Exception) { null }
            )
        } catch (_: PackageManager.NameNotFoundException) {
            _uiState.value = _uiState.value.copy(appName = packageName)
        }
    }

    fun selectPeriod(period: AppDetailPeriod) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period, isLoading = true)
        viewModelScope.launch {
            val today = repository.getDailySummary(LocalDate.now().toEpochDay())
            val todayUsage = today.apps.firstOrNull { it.packageName == packageName }
            val history = repository.getUsageHistory(packageName, period.days)
            if (_uiState.value.selectedPeriod == period) {
                _uiState.value = _uiState.value.copy(
                    todayUsage = todayUsage,
                    history = history,
                    isLoading = false
                )
            }
        }
    }

    fun setLimit(minutes: Int) {
        viewModelScope.launch {
            limitsDataStore.setLimit(packageName, minutes)
            _uiState.value = _uiState.value.copy(limitMinutes = minutes, limitEnabled = true)
        }
    }

    fun setLimitEnabled(enabled: Boolean) {
        viewModelScope.launch {
            limitsDataStore.setEnabled(packageName, enabled)
            _uiState.value = _uiState.value.copy(limitEnabled = enabled)
        }
    }

    fun removeLimit() {
        viewModelScope.launch {
            limitsDataStore.removeLimit(packageName)
            _uiState.value = _uiState.value.copy(limitMinutes = null, limitEnabled = false)
        }
    }
}
