package com.yash.chargemeterpro.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.chargemeterpro.data.local.SettingsDataStore
import com.yash.chargemeterpro.service.ChargeMeterNotificationManager
import com.yash.chargemeterpro.service.ChargingMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin pass-through over [SettingsDataStore] exposing every preference as
 * a StateFlow the Settings screen can collect, plus setter functions.
 * Deliberately not abstracting further — Settings is inherently a long
 * list of independent toggles/values, and a 1:1 mapping to the
 * underlying DataStore keeps it easy to audit that every UI control here
 * corresponds to a real, persisted preference rather than dead UI state.
 * Every setter here calls a correspondingly-named method on
 * SettingsDataStore directly — this ViewModel never touches a raw
 * Preferences.Key, keeping that as an implementation detail fully
 * contained within SettingsDataStore.kt.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val notificationManager: ChargeMeterNotificationManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private fun <T> asState(f: Flow<T>, initial: T): StateFlow<T> =
        f.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val themeMode = asState(settingsDataStore.themeMode, "dark")
    val useDynamicColor = asState(settingsDataStore.useDynamicColor, false)
    val languageTag = asState(settingsDataStore.languageTag, "en")

    val alertChargingStarted = asState(settingsDataStore.alertChargingStarted, true)
    val alertChargingCompleted = asState(settingsDataStore.alertChargingCompleted, true)
    val alert80Percent = asState(settingsDataStore.alert80Percent, false)
    val alert90Percent = asState(settingsDataStore.alert90Percent, false)
    val alert100Percent = asState(settingsDataStore.alert100Percent, true)
    val alertHighTemp = asState(settingsDataStore.alertHighTemp, true)
    val alertSlowCharging = asState(settingsDataStore.alertSlowCharging, false)
    val alertDisconnected = asState(settingsDataStore.alertDisconnected, false)
    val alertCriticalLow = asState(settingsDataStore.alertCriticalLow, true)

    val highTempThreshold = asState(settingsDataStore.highTempThresholdC, 45f)
    val criticalLowThreshold = asState(settingsDataStore.criticalLowThresholdPercent, 15)
    val slowChargeThreshold = asState(settingsDataStore.slowChargeThresholdWatts, 5.0f)
    val customMilestone = asState(settingsDataStore.customMilestonePercent, 0)

    val alwaysOnMonitorEnabled = asState(settingsDataStore.alwaysOnMonitorEnabled, false)
    val autoStartMonitoring = asState(settingsDataStore.autoStartMonitoring, true)
    val screenOnStatsEnabled = asState(settingsDataStore.screenOnStatsEnabled, true)
    val cloudBackupEnabled = asState(settingsDataStore.cloudBackupEnabled, false)
    val analyticsConsent = asState(settingsDataStore.analyticsConsent, false)

    fun setThemeMode(mode: String) = viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    fun setUseDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setUseDynamicColor(enabled) }
    fun setLanguageTag(tag: String) = viewModelScope.launch { settingsDataStore.setLanguageTag(tag) }

    fun setAlertChargingStarted(v: Boolean) = viewModelScope.launch { settingsDataStore.setAlertChargingStarted(v) }
    fun setAlertChargingCompleted(v: Boolean) = viewModelScope.launch { settingsDataStore.setAlertChargingCompleted(v) }
    fun setAlert80Percent(v: Boolean) = viewModelScope.launch { settingsDataStore.setAlert80Percent(v) }
    fun setAlert90Percent(v: Boolean) = viewModelScope.launch { settingsDataStore.setAlert90Percent(v) }
    fun setAlert100Percent(v: Boolean) = viewModelScope.launch { settingsDataStore.setAlert100Percent(v) }
    fun setAlertHighTemp(v: Boolean) = viewModelScope.launch { settingsDataStore.setAlertHighTemp(v) }
    fun setAlertSlowCharging(v: Boolean) = viewModelScope.launch { settingsDataStore.setAlertSlowCharging(v) }
    fun setAlertDisconnected(v: Boolean) = viewModelScope.launch { settingsDataStore.setAlertDisconnected(v) }
    fun setAlertCriticalLow(v: Boolean) = viewModelScope.launch { settingsDataStore.setAlertCriticalLow(v) }

    fun setHighTempThreshold(celsius: Float) = viewModelScope.launch { settingsDataStore.setHighTempThreshold(celsius) }
    fun setCriticalLowThreshold(percent: Int) = viewModelScope.launch { settingsDataStore.setCriticalLowThreshold(percent) }
    fun setSlowChargeThreshold(watts: Float) = viewModelScope.launch { settingsDataStore.setSlowChargeThreshold(watts) }
    fun setCustomMilestone(percent: Int) = viewModelScope.launch { settingsDataStore.setCustomMilestone(percent) }

    /**
     * Turning Always-On Monitor on/off doesn't just persist a preference —
     * it also starts/stops the actual foreground service, since a stale
     * "enabled" flag with no running service would be worse than useless
     * (the user would believe monitoring is active when it isn't).
     */
    fun setAlwaysOnMonitorEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setAlwaysOnMonitorEnabled(enabled)
        val intent = Intent(appContext, ChargingMonitorService::class.java)
        if (enabled) {
            ContextCompat.startForegroundService(appContext, intent)
        } else {
            appContext.stopService(intent)
        }
    }

    fun setAutoStartMonitoring(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setAutoStartMonitoring(enabled) }
    fun setScreenOnStatsEnabled(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setScreenOnStatsEnabled(enabled) }

    /** Cloud backup is off by default and this is the ONLY place it can be turned on — always with the user directly tapping the toggle. */
    fun setCloudBackupEnabled(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setCloudBackupEnabled(enabled) }
    fun setAnalyticsConsent(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setAnalyticsConsent(enabled) }

    fun setNotificationSound(uri: Uri?) {
        viewModelScope.launch { settingsDataStore.setNotificationSoundUri(uri?.toString()) }
        notificationManager.applyCustomSound(uri)
    }

    // --- Battery optimization exemption ---
    // There's no OS callback for "the user changed this in system
    // settings", so this is refreshed manually (see refreshBatteryOptimizationStatus,
    // called from SettingsScreen's lifecycle-resume) rather than being a
    // reactive Flow like everything else in this ViewModel.
    private val _isIgnoringBatteryOptimizations = MutableStateFlow(checkIgnoringBatteryOptimizations())
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()

    private fun checkIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
    }

    fun refreshBatteryOptimizationStatus() {
        _isIgnoringBatteryOptimizations.value = checkIgnoringBatteryOptimizations()
    }

    /**
     * Launches the system's per-app "ignore battery optimizations" prompt.
     * This is the standard, user-consented way to request the exemption —
     * there's no way to grant it silently, and Google Play policy
     * requires this be an explicit, user-initiated action (which it is
     * here: the user taps a button in Settings), not something requested
     * automatically on first launch.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
