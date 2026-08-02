package com.yash.chargemeterpro.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.yash.chargemeterpro.data.local.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The missing link that makes background charging monitoring actually
 * automatic (spec requirement #12) instead of depending on the user
 * remembering to flip the "Always On Charging Monitor" toggle first.
 *
 * ACTION_POWER_CONNECTED / ACTION_POWER_DISCONNECTED are protected
 * system broadcasts — no permission needed to receive them — and,
 * unlike most other implicit broadcasts, they're still deliverable to a
 * manifest-declared receiver on Android 8+ (Context-Registered-Only
 * Broadcasts restrictions explicitly carve these two out), which is why
 * this is declared in the manifest rather than registered at runtime.
 *
 * IMPORTANT CAVEAT: reliably calling startForegroundService() from
 * *this* callback is a separate concern from receiving the broadcast.
 * Android 12+'s foreground-service background-start restrictions do
 * NOT list ACTION_POWER_CONNECTED as an automatic exemption (unlike
 * BOOT_COMPLETED) — so on a stock, battery-optimized install this call
 * can occasionally be rejected with ForegroundServiceStartNotAllowedException
 * if the app process has no other active exemption at that moment. The
 * mitigation used here is that Settings prompts the user (once, when
 * they enable Smart Charging Alerts or Auto-start Monitoring — see
 * SettingsScreen's battery-optimization card) to exempt ChargeFlow from
 * battery optimization via REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; apps
 * on that allowlist are treated as exempt from this restriction. The
 * try/catch below also fails safe (logs, doesn't crash) if the OS
 * rejects the call on a device where the user hasn't granted that.
 *
 * On ACTION_POWER_DISCONNECTED there's nothing to do here directly —
 * the running service detects the disconnect via BatteryManager on its
 * own poll loop, closes out the session, and (per the self-stop logic
 * in ChargingMonitorService) stops itself if it was only running for
 * this one session.
 */
@AndroidEntryPoint
class PowerConnectionReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_POWER_CONNECTED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val autoStart = settingsDataStore.autoStartMonitoring.first()
                if (autoStart) {
                    val serviceIntent = Intent(context, ChargingMonitorService::class.java)
                    try {
                        ContextCompat.startForegroundService(context, serviceIntent)
                    } catch (e: IllegalStateException) {
                        // ForegroundServiceStartNotAllowedException extends
                        // IllegalStateException (API 31+). Fail safe rather
                        // than crash the receiver — the user can still see
                        // and record this session next time they open the
                        // app while charging, and granting the battery
                        // optimization exemption from Settings avoids this
                        // going forward.
                        android.util.Log.w(
                            "PowerConnectionReceiver",
                            "Could not start ChargingMonitorService from background: ${e.message}"
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
