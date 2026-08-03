package com.yash.chargemeterpro.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
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
 * Backs live charging monitoring, session recording, and Smart Charging
 * Alerts. Started two different ways:
 *
 *  1. The persistent "Always On Charging Monitor" toggle in Settings —
 *     runs continuously (charging or not) until the user turns it off.
 *  2. Automatically by PowerConnectionReceiver whenever the charger is
 *     physically connected (ACTION_POWER_CONNECTED), gated on the
 *     "Auto-start monitoring" preference (default: on). This is what
 *     makes background charging monitoring work even if the user never
 *     opens the app or touches the Always-On toggle — see spec
 *     requirement #12. In this mode the service stops itself once the
 *     resulting session ends (see handleSessionLifecycle), so it doesn't
 *     linger in the background between charges.
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

    // Custom-threshold "unplug now" reminder state. Unlike the fixed
    // 80/90/100% milestones (which fire once per session), the custom
    // threshold re-fires periodically for as long as the phone stays
    // plugged in past it — since the app can't stop charging itself,
    // a one-shot notification is too easy to miss/dismiss and forget.
    private var customThresholdReminderLoopCount = 0
    private val customThresholdReminderRepeatEveryLoops = 8 // ~2 min at the 15s poll interval

    // Guards against a transient plug-in start (mode 2 above) looping
    // forever if the device is unplugged again before a session ever
    // actually starts (e.g. a very brief/flaky connection). Without this,
    // handleSessionLifecycle's stopSelf() call never fires, since it only
    // runs in the "was charging, now isn't" branch.
    private var loopIterationsSinceStart = 0
    private val maxIdleIterationsBeforeSelfStop = 8 // ~2 minutes at the 15s poll interval

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialSnapshot = batteryRepository.readSnapshotNow()
        startForegroundCompat(initialSnapshot)

        loopIterationsSinceStart = 0
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
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
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

            // Safety valve for transient (plug-in-triggered) starts: if no
            // session has started after several polls and Always-On isn't
            // separately enabled, this was likely a spurious/very-brief
            // connection — stop rather than idle in the background
            // indefinitely.
            if (activeSessionId == null && !settingsDataStore.alwaysOnMonitorEnabled.first()) {
                loopIterationsSinceStart++
                if (loopIterationsSinceStart >= maxIdleIterationsBeforeSelfStop) {
                    stopSelf()
                    return
                }
            } else {
                loopIterationsSinceStart = 0
            }

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
                    customThresholdReminderLoopCount = 0
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
                customThresholdReminderLoopCount = 0
                notificationManager.cancelCustomThresholdReminder()

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

                // This service is started two different ways: (1) the
                // user's persistent "Always On Charging Monitor" toggle,
                // which should keep running indefinitely, and (2) a
                // transient start from PowerConnectionReceiver purely to
                // cover one charging session when Always-On is off. Only
                // in case (2) should the service stop itself here —
                // otherwise it would run forever in the background after
                // every single charge, defeating the point of it being
                // "auto" rather than "always".
                if (!settingsDataStore.alwaysOnMonitorEnabled.first()) {
                    stopSelf()
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

            // Custom "stop charging at X%" threshold. This is a real,
            // previously-broken feature: the value was saved to
            // SettingsDataStore but never actually read anywhere. It's
            // wired up here now. IMPORTANT REALITY CHECK: no non-root
            // Android app can physically stop charging — that control
            // isn't exposed to third-party apps by the OS. So instead
            // of pretending to stop charging, this fires a loud "unplug
            // now" reminder the moment the threshold is crossed, and
            // keeps re-firing every ~2 minutes for as long as the phone
            // stays plugged in past it, so it's actually hard to miss.
            if (settingsDataStore.customMilestoneEnabled.first()) {
                val customThreshold = settingsDataStore.customMilestonePercent.first()
                if (snapshot.batteryPercent >= customThreshold) {
                    if (customThresholdReminderLoopCount <= 0) {
                        notificationManager.notifyCustomThresholdReached(customThreshold)
                        customThresholdReminderLoopCount = customThresholdReminderRepeatEveryLoops
                    } else {
                        customThresholdReminderLoopCount--
                    }
                }
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
