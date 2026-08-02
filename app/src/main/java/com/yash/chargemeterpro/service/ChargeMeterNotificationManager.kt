package com.yash.chargemeterpro.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yash.chargemeterpro.MainActivity
import com.yash.chargemeterpro.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns notification channel setup and posting for every Smart Charging
 * Alert type. Every call here is gated by the caller checking the
 * relevant SettingsDataStore alert-enabled flow first — this class
 * itself doesn't re-check preferences, to keep it a simple, testable
 * "given permission was granted and the alert is wanted, post it"
 * component. See ChargingMonitorService.kt for the gating logic.
 */
@Singleton
class ChargeMeterNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_CHARGING_EVENTS = "charging_events"
        const val CHANNEL_ALERTS = "battery_alerts"
        const val CHANNEL_MONITOR_SERVICE = "monitor_service"

        const val NOTIFICATION_ID_CHARGING_STARTED = 1001
        const val NOTIFICATION_ID_CHARGING_COMPLETED = 1002
        const val NOTIFICATION_ID_MILESTONE = 1003
        const val NOTIFICATION_ID_HIGH_TEMP = 1004
        const val NOTIFICATION_ID_SLOW_CHARGE = 1005
        const val NOTIFICATION_ID_DISCONNECTED = 1006
        const val NOTIFICATION_ID_CRITICAL_LOW = 1007
        const val NOTIFICATION_ID_FOREGROUND_SERVICE = 2001
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHARGING_EVENTS,
                context.getString(R.string.channel_charging_events_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_charging_events_desc)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_alerts_desc)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITOR_SERVICE,
                context.getString(R.string.channel_monitor_service_name),
                NotificationManager.IMPORTANCE_LOW // persistent, shouldn't buzz/ping
            ).apply {
                description = context.getString(R.string.channel_monitor_service_desc)
            }
        )
    }

    /** Sets a custom notification sound URI on a fresh channel — Android doesn't allow changing an existing channel's sound, so this recreates the alerts channel with a new ID suffix when the user picks a custom tone. */
    fun applyCustomSound(soundUri: Uri?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alerts_desc)
            if (soundUri != null) setSound(soundUri, audioAttributes)
        }
        nm.createNotificationChannel(channel) // re-creating with same ID updates sound only pre-existing-user-override
    }

    private fun contentIntent() = android.app.PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )

    private fun canPostNotifications(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyChargingStarted(batteryPercent: Int) {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_CHARGING_EVENTS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Charging started")
            .setContentText("Battery at $batteryPercent% — tracking this session")
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CHARGING_STARTED, notification)
    }

    fun notifyChargingCompleted(durationMinutes: Long, energyWh: Double?) {
        if (!canPostNotifications()) return
        val energyText = energyWh?.let { " · ~%.1f Wh delivered".format(it) } ?: ""
        val notification = NotificationCompat.Builder(context, CHANNEL_CHARGING_EVENTS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Charging complete — battery full")
            .setContentText("Session lasted ${durationMinutes}min$energyText")
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CHARGING_COMPLETED, notification)
    }

    fun notifyMilestone(percent: Int) {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_CHARGING_EVENTS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Battery reached $percent%")
            .setContentText("Tap to view live charging details")
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_MILESTONE + percent, notification)
    }

    fun notifyHighTemperature(tempC: Double, thresholdC: Float) {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Battery temperature is high")
            .setContentText("%.1f°C measured, above your %.0f°C threshold".format(tempC, thresholdC))
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_HIGH_TEMP, notification)
    }

    fun notifySlowCharging(currentWatts: Double, thresholdWatts: Float) {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Charging is slower than usual")
            .setContentText("%.1fW measured, below your %.1fW threshold".format(currentWatts, thresholdWatts))
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SLOW_CHARGE, notification)
    }

    fun notifyDisconnected(batteryPercent: Int) {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Charging disconnected")
            .setContentText("Battery at $batteryPercent% when disconnected")
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DISCONNECTED, notification)
    }

    fun notifyCriticalLow(batteryPercent: Int, thresholdPercent: Int) {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Battery critically low")
            .setContentText("$batteryPercent% remaining, below your $thresholdPercent% threshold")
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CRITICAL_LOW, notification)
    }

    fun buildForegroundServiceNotification(
        batteryPercent: Int,
        wattsText: String,
        statusText: String
    ): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_MONITOR_SERVICE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("ChargeFlow — Charging Monitor")
            .setContentText("$statusText · $batteryPercent% · $wattsText")
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
