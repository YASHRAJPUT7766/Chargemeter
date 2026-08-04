package com.yash.chargemeterpro.util

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yash.chargemeterpro.data.local.SettingsDataStore
import com.yash.chargemeterpro.data.local.dao.DrainSampleDao
import com.yash.chargemeterpro.data.local.entity.DrainSampleEntity
import com.yash.chargemeterpro.data.repository.BatteryRepository
import com.yash.chargemeterpro.service.ChargeMeterNotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Feature #10, Battery Drain Monitor: samples battery state roughly every
 * 15 minutes (the platform-enforced floor for PeriodicWorkRequest) while
 * NOT charging. We intentionally do not run a foreground service for
 * this — a persistent service purely to watch drain would itself cost
 * meaningful battery, undermining the app's whole purpose. WorkManager's
 * periodic scheduling is the battery-efficient, OS-recommended way to do
 * this kind of "check in occasionally" background work.
 *
 * No-ops (records nothing) if the device happens to be charging at the
 * moment the worker fires — drain samples are meaningless while plugged
 * in, and that data already lives in the charging-session sample table.
 *
 * ALSO evaluates and dispatches Smart Charging Alerts that are relevant
 * while NOT charging (currently: Critical Low Battery, and High
 * Temperature since a hot device isn't only a charging-time concern).
 * This is deliberate and fixes a real gap: ChargingMonitorService only
 * runs while charging is active or "Always On Monitor" is enabled, so
 * before this, a critical-low-battery alert while unplugged could only
 * ever fire if that foreground service happened to already be alive —
 * in practice meaning the user had recently charged or had Live
 * Monitor/Always-On open. This worker already runs unconditionally in
 * the background every ~15 min regardless of any of that (see
 * DrainMonitorWorkScheduler — scheduled from MainActivity.onCreate on
 * first app launch (see MainActivity.kt) and re-scheduled after reboot
 * via BootCompletedReceiver, using enqueueUniquePeriodicWork's KEEP
 * policy so it's a safe no-op if already scheduled. Once enqueued, the
 * OS (JobScheduler under the hood) keeps re-firing it independent of
 * whether the app process is alive, and explicitly opts out
 * of the platform's battery-not-low constraint since observing low
 * battery is the whole point), so it's the correct, always-on home for
 * these checks — not a new mechanism, just finishing the wiring on an
 * existing one. Milestone/slow-charge/disconnected alerts stay in
 * ChargingMonitorService since they only make sense while a charging
 * session is actually active.
 */
@HiltWorker
class DrainMonitorWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val batteryRepository: BatteryRepository,
    private val drainSampleDao: DrainSampleDao,
    private val settingsDataStore: SettingsDataStore,
    private val notificationManager: ChargeMeterNotificationManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val snapshot = batteryRepository.readSnapshotNow()
            if (snapshot.isCharging) return Result.success()

            val screenOn = try {
                val pm = applicationContext.getSystemService(android.os.PowerManager::class.java)
                pm?.isInteractive == true
            } catch (_: Exception) {
                false
            }

            drainSampleDao.insert(
                DrainSampleEntity(
                    timestampMillis = snapshot.timestampMillis,
                    batteryPercent = snapshot.batteryPercent,
                    voltageVolts = snapshot.voltageVolts,
                    currentMilliAmps = snapshot.currentMilliAmpsNormalized,
                    temperatureCelsius = snapshot.temperatureC,
                    screenOn = screenOn
                )
            )

            // Housekeeping: keep at most ~30 days of drain samples at this
            // cadence — plenty for the drain-rate calculations shown in
            // Statistics, without the table growing unbounded forever.
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            drainSampleDao.pruneOlderThan(thirtyDaysAgo)

            evaluateNonChargingAlerts(snapshot.batteryPercent, snapshot.temperatureC)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /**
     * Critical Low Battery and High Temperature both matter while
     * unplugged, and — unlike milestone/slow-charge/disconnected alerts —
     * don't depend on a charging session existing at all. Gated on the
     * exact same SettingsDataStore flags Settings already exposes, so
     * turning an alert off there silences it here too; no new toggle is
     * introduced by this fix.
     */
    private suspend fun evaluateNonChargingAlerts(batteryPercent: Int, temperatureC: Double?) {
        if (settingsDataStore.alertCriticalLow.first()) {
            val threshold = settingsDataStore.criticalLowThresholdPercent.first()
            val alreadyFired = settingsDataStore.criticalLowAlertFiredForEpisode.first()
            if (batteryPercent <= threshold) {
                if (!alreadyFired) {
                    notificationManager.notifyCriticalLow(batteryPercent, threshold)
                    settingsDataStore.setCriticalLowAlertFiredForEpisode(true)
                }
            } else if (alreadyFired) {
                // Battery has recovered above threshold (charged, or a
                // fresh reading after the device was topped up some other
                // way) — clear the flag so the next time it drops low
                // again, that's treated as a new episode and alerts again.
                settingsDataStore.setCriticalLowAlertFiredForEpisode(false)
            }
        }

        if (temperatureC != null && settingsDataStore.alertHighTemp.first()) {
            val threshold = settingsDataStore.highTempThresholdC.first()
            if (temperatureC >= threshold) {
                notificationManager.notifyHighTemperature(temperatureC, threshold)
            }
        }
    }
}
