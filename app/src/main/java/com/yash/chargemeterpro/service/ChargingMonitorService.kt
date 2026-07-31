package com.yash.chargemeterpro.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.pm.ServiceInfoCompat
import com.yash.chargemeterpro.data.battery.ChargingPollScheduler
import com.yash.chargemeterpro.data.local.SettingsDataStore
import com.yash.chargemeterpro.data.repository.BatteryRepository
import com.yash.chargemeterpro.data.repository.ChargingSessionRepository
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.model.ChargingStatus
import com.yash.chargemeterpro.domain.usecase.PowerCalculator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the optional "Always On Charging Monitor" toggle in Settings.
 * ONLY started when the user explicitly enables that toggle (or when
 * charging begins and "Auto-start monitoring" is on) — see
 * SettingsRepository defaults, both are user-controlled.
 *
 * Responsibilities while running:
 *  1. Auto-start a charging session when plugged in, auto-stop when
 *     unplugged or full (features #10/#11 in spec: "Auto-start
 *     monitoring when charging begins" / "Auto-stop session when
 *     charging ends").
 *  2. Sample at BACKGROUND_SERVICE_INTERVAL_MS and persist samples.
 *  3. Evaluate and dispatch Smart Charging Alerts against user
 *     thresholds from SettingsDataStore.
 *  4. Push widget updates (ChargeMeterWidgetReceiver.updateAll) more
 *     frequently than the ~30min platform floor allows via
 *     updatePeriodMillis alone.
 *
 * Always shows a persistent, low-priority notification while active —
 * this is both an Android foreground-service requirement and a
 * transparency commitment: the user should never wonder whether
 * something is silently running in the background.
 */
@AndroidEntryPoint
class ChargingMonitorService : Service() {

    @Inject lateinit var batteryRepository: BatteryRepository
    @Inject lateinit var sessionRepository: ChargingSessionRepository
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var notificationManager: ChargeMeterNotificationManager

    private var serviceJob: Job? = null
    private lateinit var scope: CoroutineScope

    private var activeSessionId: Long? = null
    private var milestone80Fired = false
    private var milestone90Fired = false
    private var milestone100Fired = false
    private var lastKnownStatus: ChargingStatus = ChargingStatus.UNKNOWN

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialSnapshot = batteryRepository.readSnapshotNow()
        startForegroundCompat(initialSnapshot)

        serviceJob?.cancel()
        serviceJob = scope.launch { monitorLoop() }

        return START_STICKY
    }

    private fun startForegroundCompat(snapshot: BatterySnapshot) {
        val watts = PowerCalculator.batteryInputPowerWatts(snapshot)
        val wattsText = watts?.let { "%.1fW".format(it) } ?: "— W"
        val statusText = snapshot.chargingStatus.name.lowercase().replaceFirstChar { it.uppercase() }
        val notification = notificationManager.buildForegroundServiceNotification(
            snapshot.batteryPercent,
            wattsText,
            statusText
        )
        ServiceCompat.startForeground(
            this,
            ChargeMeterNotificationManager.NOTIFICATION_ID_FOREGROUND_SERVICE,
            notification,
            ServiceInfoCompat.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private suspend fun monitorLoop() {
        while (true) {
            val snapshot = batteryRepository.readSnapshotNow()
            handleSessionLifecycle(snapshot)
            evaluateAlerts(snapshot)
            refreshForegroundNotification(snapshot)
            com.yash.chargemeterpro.widget.ChargeMeterWidgetUpdater.pushUpdate(applicationContext, snapshot)
            lastKnownStatus = snapshot.chargingStatus
            delay(ChargingPollScheduler.BACKGROUND_SERVICE_INTERVAL_MS)
        }
    }

    private suspend fun handleSessionLifecycle(snapshot: BatterySnapshot) {
        val autoStart = settingsDataStore.autoStartMonitoring.first()

        when {
            snapshot.isCharging && activeSessionId == null -> {
                if (autoStart) {
                    val id = sessionRepository.startSession(snapshot)
                    activeSessionId = id
                    milestone80Fired = false
                    milestone90Fired = false
                    milestone100Fired = false
                    if (settingsDataStore.alertChargingStarted.first()) {
                        notificationManager.notifyChargingStarted(snapshot.batteryPercent)
                    }
                }
            }
            snapshot.isCharging && activeSessionId != null -> {
                sessionRepository.recordSample(activeSessionId!!, snapshot)
            }
            !snapshot.isCharging && activeSessionId != null -> {
                val sid = activeSessionId!!
                val wasFull = snapshot.isFull
                sessionRepository.endSession(sid, snapshot, completedNormally = true)
                activeSessionId = null

                if (wasFull && settingsDataStore.alertChargingCompleted.first()) {
                    val closedSession = sessionRepository.getSessionById(sid)
                    val durationMinutes = closedSession?.let {
                        (it.endTimeMillis ?: snapshot.timestampMillis).minus(it.startTimeMillis) / 60_000L
                    } ?: 0L
                    notificationManager.notifyChargingCompleted(
                        durationMinutes,
                        closedSession?.estimatedEnergyWattHours
                    )
                } else if (!wasFull && settingsDataStore.alertDisconnected.first()) {
                    notificationManager.notifyDisconnected(snapshot.batteryPercent)
                }
            }
        }
    }

    private suspend fun evaluateAlerts(snapshot: BatterySnapshot) {
        // Percentage milestones — fire once per session per threshold.
        if (snapshot.isCharging) {
            if (snapshot.batteryPercent >= 80 && !milestone80Fired && settingsDataStore.alert80Percent.first()) {
                notificationManager.notifyMilestone(80)
                milestone80Fired = true
            }
            if (snapshot.batteryPercent >= 90 && !milestone90Fired && settingsDataStore.alert90Percent.first()) {
                notificationManager.notifyMilestone(90)
                milestone90Fired = true
            }
            if (snapshot.batteryPercent >= 100 && !milestone100Fired && settingsDataStore.alert100Percent.first()) {
                notificationManager.notifyMilestone(100)
                milestone100Fired = true
            }
        }

        // High temperature.
        val tempC = snapshot.temperatureC
        if (tempC != null && settingsDataStore.alertHighTemp.first()) {
            val threshold = settingsDataStore.highTempThresholdC.first()
            if (tempC >= threshold) {
                notificationManager.notifyHighTemperature(tempC, threshold)
            }
        }

        // Unusually slow charging.
        if (snapshot.isCharging && settingsDataStore.alertSlowCharging.first()) {
            val watts = PowerCalculator.batteryInputPowerWatts(snapshot)
            val threshold = settingsDataStore.slowChargeThresholdWatts.first()
            if (watts != null && watts < threshold) {
                notificationManager.notifySlowCharging(watts, threshold)
            }
        }

        // Critically low battery (relevant even while this service is
        // running for charging-monitor purposes, since a user might have
        // Always On enabled generally).
        if (!snapshot.isCharging && settingsDataStore.alertCriticalLow.first()) {
            val threshold = settingsDataStore.criticalLowThresholdPercent.first()
            if (snapshot.batteryPercent <= threshold) {
                notificationManager.notifyCriticalLow(snapshot.batteryPercent, threshold)
            }
        }
    }

    private fun refreshForegroundNotification(snapshot: BatterySnapshot) {
        val watts = PowerCalculator.batteryInputPowerWatts(snapshot)
        val wattsText = watts?.let { "%.1fW".format(it) } ?: "— W"
        val statusText = snapshot.chargingStatus.name.lowercase().replaceFirstChar { it.uppercase() }
        val notification = notificationManager.buildForegroundServiceNotification(
            snapshot.batteryPercent,
            wattsText,
            statusText
        )
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm?.notify(ChargeMeterNotificationManager.NOTIFICATION_ID_FOREGROUND_SERVICE, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        scope.cancel()
    }
}
