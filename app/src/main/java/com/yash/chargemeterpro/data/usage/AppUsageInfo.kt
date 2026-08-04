package com.yash.chargemeterpro.data.usage

import android.graphics.drawable.Drawable

/**
 * One app's usage for a single day, as read from UsageStatsManager +
 * PackageManager. This is a read-only snapshot — never persisted to
 * Room, since Android's own UsageStatsManager already retains this
 * history (a rolling several-week window on most devices) far more
 * reliably than we could by re-sampling it ourselves. See
 * UsageStatsRepository for how this is assembled.
 */
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val foregroundTimeMillis: Long,
    val lastTimeUsedMillis: Long,
    val launchCount: Int,
    /** 0f..1f share of the day's total tracked foreground time. */
    val usageFraction: Float,
    /** Estimated percent of that day's total battery drop attributable to this app, if derivable. Null when not available on this device/Android version. */
    val batteryPercent: Float?
)

/** Aggregate for one calendar day across all apps. */
data class DailyUsageSummary(
    val dateEpochDay: Long,
    val totalForegroundTimeMillis: Long,
    val appCount: Int,
    val apps: List<AppUsageInfo>,
    val batteryDropPercent: Int?,
    /**
     * Real screen-unlock count for the day, from UsageEvents.KEYGUARD_HIDDEN
     * (API 28+). Null on API <28 where that event type doesn't exist —
     * the UI must show "—", never a fabricated number.
     */
    val unlockCount: Int? = null,
    /**
     * Foreground time bucketed into 24 hourly slots (index 0 = 12AM-1AM,
     * ..., index 23 = 11PM-12AM) in milliseconds, for the hour-of-day
     * sparkline. Always 24 entries; entries for hours not yet reached
     * "today" are simply 0 because no events exist there yet — not
     * because they were hidden.
     */
    val hourlyBuckets: List<Long> = List(24) { 0L }
)

/** A single bucket of usage for charting an app's history over multiple days. */
data class UsageHistoryPoint(
    val dateEpochDay: Long,
    val foregroundTimeMillis: Long
)

enum class UsagePermissionState {
    GRANTED,
    NOT_GRANTED,
    UNKNOWN
}
