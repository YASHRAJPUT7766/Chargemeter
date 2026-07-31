package com.yash.chargemeterpro.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "chargemeter_settings")

/**
 * All user-configurable preferences and small persisted scalars (like the
 * one-time capacity baseline used by BatteryCapacityEstimator) live here.
 * This is local-only Preferences DataStore — nothing here is ever
 * transmitted anywhere by this module. See ChargeMeterDatabase.kt for the
 * equivalent statement about the Room DB.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode") // "dark" | "light" | "system"
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag") // BCP-47, e.g. "en", "hi", "es"

        // --- Alert toggles ---
        val ALERT_CHARGING_STARTED = booleanPreferencesKey("alert_charging_started")
        val ALERT_CHARGING_COMPLETED = booleanPreferencesKey("alert_charging_completed")
        val ALERT_80_PERCENT = booleanPreferencesKey("alert_80_percent")
        val ALERT_90_PERCENT = booleanPreferencesKey("alert_90_percent")
        val ALERT_100_PERCENT = booleanPreferencesKey("alert_100_percent")
        val ALERT_HIGH_TEMP = booleanPreferencesKey("alert_high_temp")
        val ALERT_SLOW_CHARGING = booleanPreferencesKey("alert_slow_charging")
        val ALERT_DISCONNECTED = booleanPreferencesKey("alert_disconnected")
        val ALERT_CRITICAL_LOW = booleanPreferencesKey("alert_critical_low")

        // --- Customizable thresholds ---
        val THRESHOLD_HIGH_TEMP_C = floatPreferencesKey("threshold_high_temp_c")
        val THRESHOLD_CRITICAL_LOW_PERCENT = intPreferencesKey("threshold_critical_low_percent")
        val THRESHOLD_SLOW_CHARGE_WATTS = floatPreferencesKey("threshold_slow_charge_watts")
        val CUSTOM_MILESTONE_PERCENT = intPreferencesKey("custom_milestone_percent") // 0 = disabled

        val NOTIFICATION_SOUND_URI = stringPreferencesKey("notification_sound_uri")

        // --- Feature toggles ---
        val ALWAYS_ON_MONITOR_ENABLED = booleanPreferencesKey("always_on_monitor_enabled")
        val AUTO_START_MONITORING = booleanPreferencesKey("auto_start_monitoring")
        val SCREEN_ON_STATS_ENABLED = booleanPreferencesKey("screen_on_stats_enabled")
        val CLOUD_BACKUP_ENABLED = booleanPreferencesKey("cloud_backup_enabled") // OFF by default, explicit opt-in
        val ANALYTICS_CONSENT = booleanPreferencesKey("analytics_consent") // OFF by default, explicit opt-in

        // --- Health baseline (used by BatteryCapacityEstimator) ---
        val CAPACITY_BASELINE_MICRO_AH = longPreferencesKey("capacity_baseline_micro_ah")
        val CAPACITY_BASELINE_SET_AT = longPreferencesKey("capacity_baseline_set_at")

        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "dark" }
    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[Keys.THEME_MODE] = mode }

    val useDynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_DYNAMIC_COLOR] ?: false }
    suspend fun setUseDynamicColor(enabled: Boolean) =
        context.dataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }

    val languageTag: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE_TAG] ?: "en" }
    suspend fun setLanguageTag(tag: String) = context.dataStore.edit { it[Keys.LANGUAGE_TAG] = tag }

    // --- Alerts ---
    val alertChargingStarted: Flow<Boolean> = boolPref(Keys.ALERT_CHARGING_STARTED, true)
    suspend fun setAlertChargingStarted(enabled: Boolean) =
        context.dataStore.edit { it[Keys.ALERT_CHARGING_STARTED] = enabled }

    val alertChargingCompleted: Flow<Boolean> = boolPref(Keys.ALERT_CHARGING_COMPLETED, true)
    suspend fun setAlertChargingCompleted(enabled: Boolean) =
        context.dataStore.edit { it[Keys.ALERT_CHARGING_COMPLETED] = enabled }

    val alert80Percent: Flow<Boolean> = boolPref(Keys.ALERT_80_PERCENT, false)
    suspend fun setAlert80Percent(enabled: Boolean) =
        context.dataStore.edit { it[Keys.ALERT_80_PERCENT] = enabled }

    val alert90Percent: Flow<Boolean> = boolPref(Keys.ALERT_90_PERCENT, false)
    suspend fun setAlert90Percent(enabled: Boolean) =
        context.dataStore.edit { it[Keys.ALERT_90_PERCENT] = enabled }

    val alert100Percent: Flow<Boolean> = boolPref(Keys.ALERT_100_PERCENT, true)
    suspend fun setAlert100Percent(enabled: Boolean) =
        context.dataStore.edit { it[Keys.ALERT_100_PERCENT] = enabled }

    val alertHighTemp: Flow<Boolean> = boolPref(Keys.ALERT_HIGH_TEMP, true)
    suspend fun setAlertHighTemp(enabled: Boolean) =
        context.dataStore.edit { it[Keys.ALERT_HIGH_TEMP] = enabled }

    val alertSlowCharging: Flow<Boolean> = boolPref(Keys.ALERT_SLOW_CHARGING, false)
    suspend fun setAlertSlowCharging(enabled: Boolean) =
        context.dataStore.edit { it[Keys.ALERT_SLOW_CHARGING] = enabled }

    val alertDisconnected: Flow<Boolean> = boolPref(Keys.ALERT_DISCONNECTED, false)
    suspend fun setAlertDisconnected(enabled: Boolean) =
        context.dataStore.edit { it[Keys.ALERT_DISCONNECTED] = enabled }

    val alertCriticalLow: Flow<Boolean> = boolPref(Keys.ALERT_CRITICAL_LOW, true)
    suspend fun setAlertCriticalLow(enabled: Boolean) =
        context.dataStore.edit { it[Keys.ALERT_CRITICAL_LOW] = enabled }

    // --- Thresholds ---
    val highTempThresholdC: Flow<Float> = context.dataStore.data.map { it[Keys.THRESHOLD_HIGH_TEMP_C] ?: 45f }
    suspend fun setHighTempThreshold(celsius: Float) =
        context.dataStore.edit { it[Keys.THRESHOLD_HIGH_TEMP_C] = celsius }

    val criticalLowThresholdPercent: Flow<Int> =
        context.dataStore.data.map { it[Keys.THRESHOLD_CRITICAL_LOW_PERCENT] ?: 15 }
    suspend fun setCriticalLowThreshold(percent: Int) =
        context.dataStore.edit { it[Keys.THRESHOLD_CRITICAL_LOW_PERCENT] = percent }

    val slowChargeThresholdWatts: Flow<Float> =
        context.dataStore.data.map { it[Keys.THRESHOLD_SLOW_CHARGE_WATTS] ?: 5.0f }
    suspend fun setSlowChargeThreshold(watts: Float) =
        context.dataStore.edit { it[Keys.THRESHOLD_SLOW_CHARGE_WATTS] = watts }

    val customMilestonePercent: Flow<Int> = context.dataStore.data.map { it[Keys.CUSTOM_MILESTONE_PERCENT] ?: 0 }
    suspend fun setCustomMilestone(percent: Int) =
        context.dataStore.edit { it[Keys.CUSTOM_MILESTONE_PERCENT] = percent }

    val notificationSoundUri: Flow<String?> = context.dataStore.data.map { it[Keys.NOTIFICATION_SOUND_URI] }
    suspend fun setNotificationSoundUri(uri: String?) = context.dataStore.edit {
        if (uri == null) it.remove(Keys.NOTIFICATION_SOUND_URI) else it[Keys.NOTIFICATION_SOUND_URI] = uri
    }

    // --- Feature toggles ---
    val alwaysOnMonitorEnabled: Flow<Boolean> = boolPref(Keys.ALWAYS_ON_MONITOR_ENABLED, false)
    suspend fun setAlwaysOnMonitorEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.ALWAYS_ON_MONITOR_ENABLED] = enabled }

    val autoStartMonitoring: Flow<Boolean> = boolPref(Keys.AUTO_START_MONITORING, true)
    suspend fun setAutoStartMonitoring(enabled: Boolean) =
        context.dataStore.edit { it[Keys.AUTO_START_MONITORING] = enabled }

    val screenOnStatsEnabled: Flow<Boolean> = boolPref(Keys.SCREEN_ON_STATS_ENABLED, true)
    suspend fun setScreenOnStatsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.SCREEN_ON_STATS_ENABLED] = enabled }

    /**
     * OFF by default — cloud backup is an explicit opt-in per the privacy
     * requirements. Flipping this alone does not implement any actual
     * sync; it's the gate that a (separately implemented, not included
     * by default) sync module must check before ever calling out to a
     * server.
     */
    val cloudBackupEnabled: Flow<Boolean> = boolPref(Keys.CLOUD_BACKUP_ENABLED, false)
    suspend fun setCloudBackupEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.CLOUD_BACKUP_ENABLED] = enabled }

    /** OFF by default — analytics only ever run if the user explicitly consents here. */
    val analyticsConsent: Flow<Boolean> = boolPref(Keys.ANALYTICS_CONSENT, false)
    suspend fun setAnalyticsConsent(enabled: Boolean) =
        context.dataStore.edit { it[Keys.ANALYTICS_CONSENT] = enabled }

    // --- Capacity baseline (see BatteryCapacityEstimator.kt) ---
    val capacityBaselineMicroAh: Flow<Long?> = context.dataStore.data.map { it[Keys.CAPACITY_BASELINE_MICRO_AH] }

    /** Sets the baseline ONLY if one isn't already stored — we never silently overwrite a user's original baseline reading. */
    suspend fun setCapacityBaselineIfAbsent(microAh: Long) {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.CAPACITY_BASELINE_MICRO_AH] == null) {
                prefs[Keys.CAPACITY_BASELINE_MICRO_AH] = microAh
                prefs[Keys.CAPACITY_BASELINE_SET_AT] = System.currentTimeMillis()
            }
        }
    }

    suspend fun resetCapacityBaseline() {
        context.dataStore.edit {
            it.remove(Keys.CAPACITY_BASELINE_MICRO_AH)
            it.remove(Keys.CAPACITY_BASELINE_SET_AT)
        }
    }

    val onboardingComplete: Flow<Boolean> = boolPref(Keys.ONBOARDING_COMPLETE, false)
    suspend fun setOnboardingComplete(complete: Boolean) =
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }

    private fun boolPref(
        key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
        default: Boolean
    ): Flow<Boolean> = context.dataStore.data.map { it[key] ?: default }
}
