package com.yash.chargemeterpro.util

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yash.chargemeterpro.data.local.dao.DrainSampleDao
import com.yash.chargemeterpro.data.local.entity.DrainSampleEntity
import com.yash.chargemeterpro.data.repository.BatteryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

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
 */
@HiltWorker
class DrainMonitorWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val batteryRepository: BatteryRepository,
    private val drainSampleDao: DrainSampleDao
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

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
