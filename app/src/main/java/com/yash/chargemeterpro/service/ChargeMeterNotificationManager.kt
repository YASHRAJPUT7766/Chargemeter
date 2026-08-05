package com.yash.chargemeterpro.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
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
 *
 * SOUND DESIGN: each alert type gets its OWN notification channel, not
 * a shared one. Android only lets you set a sound per-*channel*, not
 * per-notification — so this is the only way for "charging started" to
 * genuinely sound different from "battery critical" instead of both
 * silently falling back to one shared default tone. Each channel below
 * is pre-assigned a different built-in system ringtone/notification
 * sound at creation time so every alert is distinguishable by ear
 * without the user having to configure anything.
 *
 * ICON: every notification here uses R.drawable.ic_stat_notify, a
 * proper white-silhouette-on-transparent status bar icon generated
 * from the app's own monochrome launcher layer — never a stock
 * Android system icon — so alerts are recognizable as Battery Stats' at
 * a glance in the status bar and shade.
 */
@Singleton
class ChargeMeterNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_CHARGING_STARTED = "charging_started"
        const val CHANNEL_CHARGING_COMPLETED = "charging_completed"
        const val CHANNEL_MILESTONE = "charge_milestone"
        const val CHANNEL_HIGH_TEMP = "high_temp_alert"
        const val CHANNEL_SLOW_CHARGE = "slow_charge_alert"
        const val CHANNEL_DISCONNECTED = "disconnected_alert"
        const val CHANNEL_CRITICAL_LOW = "critical_low_alert"
        const val CHANNEL_MONITOR_SERVICE = "monitor_service"

        const val NOTIFICATION_ID_CHARGING_STARTED = 1001
        const val NOTIFICATION_ID_CHARGING_COMPLETED = 1002
        const val NOTIFICATION_ID_MILESTONE = 1003
        const val NOTIFICATION_ID_HIGH_TEMP = 1004
        const val NOTIFICATION_ID_SLOW_CHARGE = 1005
        const val NOTIFICATION_ID_DISCONNECTED = 1006
        const val NOTIFICATION_ID_CRITICAL_LOW = 1007
        const val NOTIFICATION_ID_CUSTOM_THRESHOLD = 1008
        const val NOTIFICATION_ID_FOREGROUND_SERVICE = 2001
    }

    init {
        createChannels()
    }

    /**
     * One channel per alert type, each pre-loaded with a different
     * built-in system sound so every alert is audibly distinct out of
     * the box. Uses RingtoneManager's TYPE_NOTIFICATION pool (device
     * built-ins, always present, no extra permission needed) rather
     * than bundling custom audio files.
     */
    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        fun soundAt(index: Int): Uri? = distinctNotificationSounds().getOrNull(index)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHARGING_STARTED,
                context.getString(R.string.channel_charging_started_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_charging_started_desc)
                soundAt(0)?.let { setSound(it, audioAttrs) }
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHARGING_COMPLETED,
                context.getString(R.string.channel_charging_completed_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_charging_completed_desc)
                soundAt(1)?.let { setSound(it, audioAttrs) }
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MILESTONE,
                context.getString(R.string.channel_milestone_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_milestone_desc)
                soundAt(2)?.let { setSound(it, audioAttrs) }
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_HIGH_TEMP,
                context.getString(R.string.channel_high_temp_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_high_temp_desc)
                soundAt(3)?.let { setSound(it, audioAttrs) }
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SLOW_CHARGE,
                context.getString(R.string.channel_slow_charge_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_slow_charge_desc)
                soundAt(4)?.let { setSound(it, audioAttrs) }
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DISCONNECTED,
                context.getString(R.string.channel_disconnected_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_disconnected_desc)
                soundAt(5)?.let { setSound(it, audioAttrs) }
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CRITICAL_LOW,
                context.getString(R.string.channel_critical_low_name),
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = context.getString(R.string.channel_critical_low_desc)
                soundAt(6)?.let { setSound(it, audioAttrs) }
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

    /**
     * Pulls up to 7 distinct built-in notification sounds from the
     * device's ringtone pool, in a stable order, so each alert channel
     * above gets a different one. Falls back to the single system
     * default for any index beyond what the device offers (some very
     * minimal ROMs ship few built-ins) rather than crashing or leaving
     * a channel silent.
     */
    private fun distinctNotificationSounds(): List<Uri> {
        val manager = RingtoneManager(context).apply {
            setType(RingtoneManager.TYPE_NOTIFICATION)
        }
        val cursor = manager.cursor
        val uris = mutableListOf<Uri>()
        try {
            while (cursor.moveToNext() && uris.size < 7) {
                uris.add(manager.getRingtoneUri(cursor.position))
            }
        } catch (_: Exception) {
            // fall through to whatever was collected
        }
        val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        while (uris.size < 7) uris.add(fallback)
        return uris
    }

    /**
     * Lets the user override the milestone-alert channel's sound with a
     * custom tone from Settings. Android doesn't allow changing an
     * existing channel's sound after creation, so this recreates
     * CHANNEL_MILESTONE with the new sound — safe since the channel ID
     * stays the same and the OS treats this as an update, not a
     * duplicate.
     */
    fun applyCustomSound(soundUri: Uri?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_MILESTONE,
            context.getString(R.string.channel_milestone_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_milestone_desc)
            val effectiveUri = soundUri ?: distinctNotificationSounds().getOrNull(2)
            if (effectiveUri != null) setSound(effectiveUri, audioAttributes)
        }
        nm.createNotificationChannel(channel)
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

    private fun baseBuilder(channelId: String) = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_stat_notify)
        .setContentIntent(contentIntent())
        .setAutoCancel(true)

    fun notifyChargingStarted(batteryPercent: Int) {
        if (!canPostNotifications()) return
        val notification = baseBuilder(CHANNEL_CHARGING_STARTED)
            .setContentTitle("Charging started")
            .setContentText("Battery at $batteryPercent% — tracking this session")
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CHARGING_STARTED, notification)
    }

    fun notifyChargingCompleted(durationMinutes: Long, energyWh: Double?) {
        if (!canPostNotifications()) return
        val energyText = energyWh?.let { " · ~%.1f Wh delivered".format(it) } ?: ""
        val notification = baseBuilder(CHANNEL_CHARGING_COMPLETED)
            .setContentTitle("Charging complete — battery full")
            .setContentText("Session lasted ${durationMinutes}min$energyText")
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CHARGING_COMPLETED, notification)
    }

    fun notifyMilestone(percent: Int) {
        if (!canPostNotifications()) return
        val notification = baseBuilder(CHANNEL_MILESTONE)
            .setContentTitle("Battery reached $percent%")
            .setContentText("Tap to view live charging details")
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_MILESTONE + percent, notification)
    }

    /**
     * Fires for the user's custom charge threshold (Settings → Custom
     * Thresholds → "Stop charging at"). Since a non-root Android app
     * cannot physically stop charging — that control isn't exposed to
     * third-party apps by the OS — this is deliberately a loud,
     * high-priority "unplug now" reminder rather than a silent
     * milestone ping, and re-fires (see ChargingMonitorService) every
     * couple of minutes for as long as the phone stays plugged in past
     * the threshold, so it's hard to miss even if the first one is
     * dismissed or the phone is face-down.
     */
    fun notifyCustomThresholdReached(percent: Int) {
        if (!canPostNotifications()) return
        val notification = baseBuilder(CHANNEL_MILESTONE)
            .setContentTitle("Unplug now — reached your $percent% limit")
            .setContentText("Battery Stats can't stop charging automatically, but you're past your custom limit")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CUSTOM_THRESHOLD, notification)
    }

    fun cancelCustomThresholdReminder() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_CUSTOM_THRESHOLD)
    }

    fun notifyHighTemperature(tempC: Double, thresholdC: Float) {
        if (!canPostNotifications()) return
        val notification = baseBuilder(CHANNEL_HIGH_TEMP)
            .setContentTitle("Battery temperature is high")
            .setContentText("%.1f°C measured, above your %.0f°C threshold".format(tempC, thresholdC))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_HIGH_TEMP, notification)
    }

    fun notifySlowCharging(currentWatts: Double, thresholdWatts: Float) {
        if (!canPostNotifications()) return
        val notification = baseBuilder(CHANNEL_SLOW_CHARGE)
            .setContentTitle("Charging is slower than usual")
            .setContentText("%.1fW measured, below your %.1fW threshold".format(currentWatts, thresholdWatts))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SLOW_CHARGE, notification)
    }

    fun notifyDisconnected(batteryPercent: Int) {
        if (!canPostNotifications()) return
        val notification = baseBuilder(CHANNEL_DISCONNECTED)
            .setContentTitle("Charging disconnected")
            .setContentText("Battery at $batteryPercent% when disconnected")
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DISCONNECTED, notification)
    }

    fun notifyCriticalLow(batteryPercent: Int, thresholdPercent: Int) {
        if (!canPostNotifications()) return
        val notification = baseBuilder(CHANNEL_CRITICAL_LOW)
            .setContentTitle("Battery critically low")
            .setContentText("$batteryPercent% remaining, below your $thresholdPercent% threshold")
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
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("Battery Stats — Charging Monitor")
            .setContentText("$statusText · $batteryPercent% · $wattsText")
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
