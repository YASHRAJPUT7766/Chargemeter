package com.yash.chargemeterpro.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.yash.chargemeterpro.data.local.SettingsDataStore
import com.yash.chargemeterpro.util.DrainMonitorWorkScheduler
import com.yash.chargemeterpro.util.UsageWidgetWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Only re-attaches background work the user had already, explicitly
 * enabled before reboot — this receiver never turns anything on that
 * wasn't already on. Two responsibilities:
 *  1. Re-enqueue the periodic drain-monitor WorkManager job (this is
 *     lightweight, always safe to (re)schedule).
 *  2. Restart the "Always On Charging Monitor" foreground service, but
 *     ONLY if that setting was enabled AND the device is currently
 *     plugged in — we do not start a foreground service in the
 *     background purely on boot for a not-currently-charging device,
 *     since Android restricts starting foreground services from a
 *     BOOT_COMPLETED receiver in various OEM/OS-version combinations,
 *     and it isn't needed until charging actually begins anyway (the
 *     drain-monitor Worker + broadcast receivers pick things up from
 *     there once the user next plugs in and opens the app, or via
 *     ACTION_POWER_CONNECTED if we're still eligible to start a
 *     foreground service at that moment).
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var drainMonitorWorkScheduler: DrainMonitorWorkScheduler
    @Inject lateinit var usageWidgetWorkScheduler: UsageWidgetWorkScheduler

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                drainMonitorWorkScheduler.schedule()
                usageWidgetWorkScheduler.schedule()

                val alwaysOnEnabled = settingsDataStore.alwaysOnMonitorEnabled.first()
                if (alwaysOnEnabled) {
                    val bm = ContextCompat.getSystemService(context, android.os.BatteryManager::class.java)
                    val isCharging = bm?.isCharging == true
                    if (isCharging) {
                        val serviceIntent = Intent(context, ChargingMonitorService::class.java)
                        ContextCompat.startForegroundService(context, serviceIntent)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
