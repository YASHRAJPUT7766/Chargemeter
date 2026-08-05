package com.yash.chargemeterpro.data.usage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appLimitsDataStore by preferencesDataStore(name = "battery_stats_app_limits")

/**
 * Per-app daily usage limit ("App Restrictions", spec item #10). Scope
 * is deliberately UI + tracking only: this stores a limit and lets the
 * Usage feature compare today's real foreground time against it to show
 * progress and an over-limit warning state. It does NOT block, close,
 * or otherwise interfere with the target app — real enforcement would
 * require an AccessibilityService or a persistent foreground watcher
 * (extra permissions, real battery cost, and App Restriction/blocking
 * features specifically draw elevated Play Store policy scrutiny), which
 * is out of scope here by design, not by omission.
 *
 * Stored as one JSON-encoded map under a single DataStore key rather
 * than a Room table, since this is a small, purely-local preference set
 * with no history/relational needs — adding a Room entity + DAO +
 * migration for "package name -> minutes" would be substantial ceremony
 * for what's really just a settings blob.
 */
@Singleton
class AppLimitsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Serializable
    data class AppLimit(
        val packageName: String,
        val dailyLimitMinutes: Int,
        val enabled: Boolean = true
    )

    private object Keys {
        val LIMITS_JSON = stringPreferencesKey("app_limits_json")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val limits: Flow<Map<String, AppLimit>> = context.appLimitsDataStore.data.map { prefs ->
        val raw = prefs[Keys.LIMITS_JSON] ?: return@map emptyMap()
        try {
            json.decodeFromString<List<AppLimit>>(raw).associateBy { it.packageName }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun setLimit(packageName: String, dailyLimitMinutes: Int) {
        updateAll { current ->
            current + (packageName to AppLimit(packageName, dailyLimitMinutes, enabled = true))
        }
    }

    suspend fun setEnabled(packageName: String, enabled: Boolean) {
        updateAll { current ->
            val existing = current[packageName] ?: return@updateAll current
            current + (packageName to existing.copy(enabled = enabled))
        }
    }

    suspend fun removeLimit(packageName: String) {
        updateAll { current -> current - packageName }
    }

    private suspend fun updateAll(transform: (Map<String, AppLimit>) -> Map<String, AppLimit>) {
        context.appLimitsDataStore.edit { prefs ->
            val raw = prefs[Keys.LIMITS_JSON]
            val current = raw?.let {
                try {
                    json.decodeFromString<List<AppLimit>>(it).associateBy { l -> l.packageName }
                } catch (_: Exception) {
                    emptyMap()
                }
            } ?: emptyMap()
            val updated = transform(current)
            prefs[Keys.LIMITS_JSON] = json.encodeToString(updated.values.toList())
        }
    }
}
