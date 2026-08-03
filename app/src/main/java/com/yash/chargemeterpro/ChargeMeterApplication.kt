package com.yash.chargemeterpro

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.yash.chargemeterpro.data.repository.BatteryRepository
import com.yash.chargemeterpro.data.repository.ChargingSessionRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ChargeMeterApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var batteryRepository: BatteryRepository
    @Inject lateinit var sessionRepository: ChargingSessionRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Runs on every process start (app opened from launcher, brought
        // back by a broadcast, or resurrected by the OS for any other
        // reason) — closes out any charging session left stuck "active"
        // by a background process that died mid-charge without anything
        // else catching it. See ChargingSessionRepository.
        // reconcileOrphanedActiveSession for the full rationale. Cheap
        // (single DB read on the common "nothing orphaned" path) and
        // always safe to run unconditionally.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val currentSnapshot = batteryRepository.readSnapshotNow()
            sessionRepository.reconcileOrphanedActiveSession(currentSnapshot)
        }
    }
}
