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
 * ACTION_POWER_DISCONNECTED is handled here too, and deliberately does
 * the same startForegroundService() call as connect rather than nothing.
 * The original assumption was "the already-running service will notice
 * the unplug on its own next poll tick" — true when the service is
 * alive, but there are real gaps where it isn't:
 *   - Always-On Monitor is off (default) and the transient service
 *     instance from plug-in already self-stopped for an unrelated
 *     reason (process death, OS memory pressure, battery optimization
 *     killing it) before the user actually unplugged.
 *   - The user plugged in while the app's Live Monitor screen was open
 *     (which used to record sessions locally via its own ViewModel) and
 *     then left that screen or closed the app before unplugging — the
 *     ViewModel is destroyed with the screen/process and was never a
 *     reliable session owner in the first place.
 * Starting the service here is safe and idempotent even if it's already
 * running (Service#onStartCommand just restarts its poll loop), and its
 * very first poll tick will see isCharging == false, read the *real*
 * current snapshot (percent, voltage, timestamp), call endSession() with
 * that accurate data instead of stale/missing values, and then stop
 * itself per the existing self-stop logic in ChargingMonitorService.
 */
@AndroidEntryPoint
class PowerConnectionReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_POWER_CONNECTED && action != Intent.ACTION_POWER_DISCONNECTED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // On connect, respect the "Auto-start monitoring" toggle as
                // before. On disconnect, always try to (re)start the
                // service regardless of that toggle — this isn't "starting
                // a new monitoring session", it's giving whatever session
                // may already be active one last chance to close out with
                // real data instead of being left stuck as "Charging" in
                // History forever. If there's no active session, the
                // service's own idle self-stop logic quietly stops it
                // again within a couple of poll ticks, so this is cheap.
                val autoStart = action == Intent.ACTION_POWER_DISCONNECTED ||
                    settingsDataStore.autoStartMonitoring.first()
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

                // Charging Display: opt-in full-screen lock-screen readout,
                // launched on plug-in only (never on disconnect). Gated
                // strictly on the user's explicit toggle in Settings —
                // launching an activity from a background receiver like
                // this is exactly the kind of surprising behavior that
                // should never happen without clear consent.
                if (action == Intent.ACTION_POWER_CONNECTED &&
                    settingsDataStore.chargingDisplayEnabled.first()
                ) {
                    try {
                        context.startActivity(
                            com.yash.chargemeterpro.ui.screens.chargingdisplay.ChargingDisplayActivity.launchIntent(context)
                        )
                    } catch (e: Exception) {
                        // Background activity-start restrictions (API 29+)
                        // can reject this in some states (e.g. app fully
                        // killed with no exemption). Fail safe — same
                        // battery-optimization exemption flow mentioned
                        // above makes this reliable in practice.
                        android.util.Log.w(
                            "PowerConnectionReceiver",
                            "Could not start ChargingDisplayActivity from background: ${e.message}"
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
