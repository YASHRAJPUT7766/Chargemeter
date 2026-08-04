package com.yash.chargemeterpro.data.checkup

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads real, currently-in-effect device power settings for the Battery
 * Checkup flow. Every value here comes straight from a live system API —
 * nothing is cached, simulated, or guessed — because the whole point of
 * the checkup is to tell the user what their device is ACTUALLY doing
 * right now.
 *
 * All three reads use only normal, non-runtime-dangerous permissions (or
 * none at all): PowerManager.isPowerSaveMode requires no permission,
 * and Settings.System.SCREEN_BRIGHTNESS / SCREEN_OFF_TIMEOUT are readable
 * by any app without a permission grant (they're not part of the
 * write-settings-protected surface). If a read ever fails on some OEM
 * skin, we surface null rather than a fabricated value so the checkup
 * can honestly say "couldn't be read" instead of guessing.
 */
data class DeviceDiagnostics(
    val isPowerSaveMode: Boolean?,
    /** 0-255 raw system brightness value, or null if unreadable. */
    val screenBrightness: Int?,
    /** 0-1f normalized brightness, or null if unreadable. */
    val screenBrightnessFraction: Float?,
    val screenTimeoutMillis: Int?,
    val isAdaptiveBrightnessOn: Boolean?
)

@Singleton
class DeviceDiagnosticsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun readNow(): DeviceDiagnostics {
        val powerSaveMode = try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isPowerSaveMode
        } catch (_: Exception) {
            null
        }

        val brightness = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Exception) {
            null
        }

        val timeout = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        } catch (_: Exception) {
            null
        }

        val adaptiveBrightness = try {
            val mode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (_: Exception) {
            null
        }

        return DeviceDiagnostics(
            isPowerSaveMode = powerSaveMode,
            screenBrightness = brightness,
            screenBrightnessFraction = brightness?.let { (it / 255f).coerceIn(0f, 1f) },
            screenTimeoutMillis = timeout,
            isAdaptiveBrightnessOn = adaptiveBrightness
        )
    }
}
