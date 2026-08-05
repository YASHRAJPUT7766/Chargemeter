package com.yash.chargemeterpro.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import com.yash.chargemeterpro.data.local.dao.DrainSampleDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Android's UsageStatsManager + PackageManager to build the data
 * behind the Usage dashboard (spec items #6-#9). Deliberately does NOT
 * duplicate what UsageStatsManager already tracks into our own Room
 * database — Android itself retains a rolling multi-week history of
 * per-app foreground time, which is both more accurate (it sees every
 * app, including ones opened while Battery Stats wasn't running at all)
 * and avoids us maintaining a second, inevitably-drifting copy of the
 * same data. We cap what we expose to 15 days per the spec even though
 * the OS often retains more.
 *
 * REQUIRES the PACKAGE_USAGE_STATS special app op, granted only via
 * Settings.ACTION_USAGE_ACCESS_SETTINGS — never requestable as a normal
 * runtime permission. See hasUsageAccess()/usageAccessSettingsIntent().
 *
 * Per-app BATTERY percent: Android does not expose a public,
 * per-app-per-day battery-drain API to third-party apps (that data
 * backs the system Battery Usage screen but isn't in any SDK surface).
 * batteryPercentFor() below derives a reasonable *estimate* by
 * distributing the device's total measured battery percent drop for the
 * day across apps in proportion to their foreground time share — this
 * is clearly an estimate, not a system-measured value, and every UI
 * that shows it must label it as such (see AppUsageInfo.batteryPercent
 * doc and the "estimated" label used in UsageScreen/AppDetailScreen).
 */
@Singleton
class UsageStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val drainSampleDao: DrainSampleDao
) {
    companion object {
        const val MAX_HISTORY_DAYS = 15
    }

    private val usageStatsManager: UsageStatsManager? by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }
    private val packageManager: PackageManager by lazy { context.packageManager }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }

    /** Earliest day (inclusive) the Usage screen will let the user swipe back to. */
    fun earliestAvailableEpochDay(): Long = LocalDate.now().toEpochDay() - (MAX_HISTORY_DAYS - 1)

    /**
     * Builds the full per-app breakdown for one local calendar day.
     * [dateEpochDay] is LocalDate.toEpochDay() — days since 1970-01-01,
     * used throughout this feature instead of raw millis so "swipe to
     * previous day" is a trivial -1/+1 rather than juggling timezone-
     * aware millis math at every call site.
     */
    suspend fun getDailySummary(dateEpochDay: Long): DailyUsageSummary = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.ofEpochDay(dateEpochDay)
        val startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillisRaw = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val endMillis = minOf(endMillisRaw, now)

        val manager = usageStatsManager
        if (manager == null || !hasUsageAccess() || startMillis >= endMillis) {
            return@withContext DailyUsageSummary(dateEpochDay, 0L, 0, emptyList(), null)
        }

        val foregroundTimeByPackage = aggregateForegroundTime(manager, startMillis, endMillis)
        val launchCountByPackage = countLaunches(manager, startMillis, endMillis)
        val unlockCount = countUnlocks(manager, startMillis, endMillis)
        val hourlyBuckets = bucketForegroundTimeByHour(manager, startMillis, endMillis, zone)
        val lastUsedByPackage = mutableMapOf<String, Long>()

        val statsList = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMillis, endMillis)
        statsList?.forEach { stat ->
            if (stat.lastTimeUsed in startMillis..endMillis) {
                val existing = lastUsedByPackage[stat.packageName] ?: 0L
                if (stat.lastTimeUsed > existing) lastUsedByPackage[stat.packageName] = stat.lastTimeUsed
            }
        }

        val totalForegroundMillis = foregroundTimeByPackage.values.sum()
        val batteryDropPercent = estimateBatteryDropForDay(dateEpochDay, zone)

        val apps = foregroundTimeByPackage.entries
            .filter { it.value > 0L }
            .mapNotNull { (pkg, timeMillis) ->
                val info = resolveAppInfo(pkg) ?: return@mapNotNull null
                AppUsageInfo(
                    packageName = pkg,
                    appName = info.first,
                    icon = info.second,
                    foregroundTimeMillis = timeMillis,
                    lastTimeUsedMillis = lastUsedByPackage[pkg] ?: 0L,
                    launchCount = launchCountByPackage[pkg] ?: 0,
                    usageFraction = 0f, // placeholder, recomputed below against the *visible* total
                    batteryPercent = null // placeholder, recomputed below against the *visible* total
                )
            }
            .sortedByDescending { it.foregroundTimeMillis }

        // Recompute fraction/battery-share against the sum of only the apps
        // actually shown, not the pre-filter total — otherwise a handful of
        // headless packages (still legitimately hidden, e.g. "android"
        // itself in rare edge cases) would make every visible app's % and
        // the visible list's own total look like they don't add up, even
        // though each individual app's raw minutes were always correct.
        val visibleTotalMillis = apps.sumOf { it.foregroundTimeMillis }
        val appsWithShares = apps.map { app ->
            val fraction = if (visibleTotalMillis > 0) app.foregroundTimeMillis.toFloat() / visibleTotalMillis else 0f
            app.copy(
                usageFraction = fraction,
                batteryPercent = batteryDropPercent?.let { it * fraction }
            )
        }

        DailyUsageSummary(
            dateEpochDay = dateEpochDay,
            totalForegroundTimeMillis = visibleTotalMillis,
            appCount = appsWithShares.size,
            apps = appsWithShares,
            batteryDropPercent = batteryDropPercent?.let { Math.round(it) },
            unlockCount = unlockCount,
            hourlyBuckets = hourlyBuckets
        )
    }

    /** Usage history for one app across the last [days] days (most recent last), for the App Detail chart. */
    suspend fun getUsageHistory(packageName: String, days: Int): List<UsageHistoryPoint> = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toEpochDay()
        val earliest = maxOf(earliestAvailableEpochDay(), today - (days - 1))
        (earliest..today).map { epochDay ->
            val summary = getDailySummary(epochDay)
            val millis = summary.apps.firstOrNull { it.packageName == packageName }?.foregroundTimeMillis ?: 0L
            UsageHistoryPoint(epochDay, millis)
        }
    }

    /**
     * Walks raw UsageEvents rather than trusting queryUsageStats' own
     * totalTimeInForeground directly, since that field is aggregated
     * over whatever window the OS feels like on some OEM skins. Summing
     * MOVE_TO_FOREGROUND -> MOVE_TO_BACKGROUND (and
     * ACTIVITY_RESUMED/PAUSED on API 29+) pairs ourselves, clipped to
     * [startMillis, endMillis], gives a consistent per-day number no
     * matter the device.
     *
     * Real-device event-stream quirks this specifically guards against —
     * all three previously caused apps to be undercounted, dropped
     * entirely, or credited with a bogus duration:
     *
     * 1. ORPHANED PAUSE: an app opened *before* startMillis (e.g. before
     *    local midnight, still open when the new day's query window
     *    begins) has no resume event inside [startMillis, endMillis] —
     *    only the pause when it's eventually backgrounded. Previously
     *    this pause was silently discarded (`?: continue`), losing that
     *    app's entire time from midnight to the pause. It's now treated
     *    as "was already open at window start" and credited from
     *    startMillis instead.
     *
     * 2. DUPLICATE RESUME: on API 29+, real devices commonly emit BOTH
     *    MOVE_TO_FOREGROUND and ACTIVITY_RESUMED for the same single
     *    transition (and both BACKGROUND/PAUSED on exit), a few
     *    milliseconds apart. Treating both event types as equally valid
     *    "resume" signals meant a second resume for an already-open
     *    package overwrote its stored open-timestamp, silently losing
     *    the gap between the two events. A resume for a package already
     *    marked open is now ignored — it's the same real transition
     *    reported twice, not a new one.
     *
     * 3. DUPLICATE PAUSE: the mirror of #2 on the way out — a second
     *    pause event arriving for a package that the *first* pause of
     *    the pair already closed out. Without tracking this separately
     *    from a genuine orphan (#1), this second pause would be
     *    misread as "no resume seen" and incorrectly credited a bogus
     *    duration counted all the way from startMillis. closedSincePause
     *    tracks packages resolved this way so a duplicate pause is
     *    correctly ignored instead, while a real orphan (never in this
     *    set) still gets credited.
     */
    private fun aggregateForegroundTime(
        manager: UsageStatsManager,
        startMillis: Long,
        endMillis: Long
    ): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        val openedAt = mutableMapOf<String, Long>()
        val closedSincePause = mutableSetOf<String>()
        val events = manager.queryEvents(startMillis, endMillis)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val isResume = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (android.os.Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            val isPause = event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                (android.os.Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_PAUSED)

            when {
                isResume -> {
                    // Ignore a resume for a package already marked open —
                    // see DUPLICATE RESUME above. A genuine new open
                    // clears the "just closed" marker so a *later* pause
                    // is treated normally again.
                    if (!openedAt.containsKey(pkg)) openedAt[pkg] = event.timeStamp
                    closedSincePause.remove(pkg)
                }
                isPause -> {
                    if (pkg in closedSincePause) {
                        // Duplicate pause for a package the previous pause
                        // already closed out — see DUPLICATE PAUSE above.
                        // Not an orphan; just ignore it.
                    } else {
                        // No open entry here means either a genuine
                        // ORPHANED PAUSE (carried over from before
                        // startMillis) or this same case — either way,
                        // crediting from startMillis is correct since
                        // this is the first pause seen for the package.
                        val start = openedAt.remove(pkg) ?: startMillis
                        val duration = (event.timeStamp - start).coerceIn(0L, endMillis - startMillis)
                        if (duration > 0) result[pkg] = (result[pkg] ?: 0L) + duration
                        closedSincePause.add(pkg)
                    }
                }
            }
        }
        // Any app still "open" at window end (e.g. currently in foreground) counts up to endMillis.
        openedAt.forEach { (pkg, start) ->
            val duration = (endMillis - start).coerceIn(0L, endMillis - startMillis)
            if (duration > 0) result[pkg] = (result[pkg] ?: 0L) + duration
        }
        return result
    }

    private fun countLaunches(manager: UsageStatsManager, startMillis: Long, endMillis: Long): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        val events = manager.queryEvents(startMillis, endMillis)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val isResume = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (android.os.Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            if (isResume) counts[pkg] = (counts[pkg] ?: 0) + 1
        }
        return counts
    }

    /**
     * Counts real screen unlocks for the window via UsageEvents.KEYGUARD_HIDDEN
     * — fired by the system exactly when the lock screen is dismissed.
     * Only exists from API 28 onward; returns null (not 0) below that so
     * the UI can distinguish "genuinely zero unlocks" from "this device
     * can't report unlocks at all" and show "—" instead of a false zero.
     */
    private fun countUnlocks(manager: UsageStatsManager, startMillis: Long, endMillis: Long): Int? {
        if (android.os.Build.VERSION.SDK_INT < 28) return null
        var count = 0
        val events = manager.queryEvents(startMillis, endMillis)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) count++
        }
        return count
    }

    /**
     * Re-walks the same resume/pause event stream as aggregateForegroundTime,
     * but splits each open interval across local-hour boundaries so a
     * session that starts at 11:50pm and ends at 12:10am is credited
     * 10min to hour-23 and 10min to hour-0 of the *next* day — never all
     * 20min dumped into one bucket, which is what previously made single
     * long sessions look like implausible spikes on the hourly graph.
     * Only minutes that actually fall inside [startMillis, endMillis] are
     * counted, so a day can never sum to more than 24h here either.
     */
    private fun bucketForegroundTimeByHour(
        manager: UsageStatsManager,
        startMillis: Long,
        endMillis: Long,
        zone: ZoneId
    ): List<Long> {
        val buckets = LongArray(24)
        val openedAt = mutableMapOf<String, Long>()
        val closedSincePause = mutableSetOf<String>()
        val events = manager.queryEvents(startMillis, endMillis)
        val event = UsageEvents.Event()

        fun creditInterval(from: Long, to: Long) {
            var cursor = from
            while (cursor < to) {
                val hour = java.time.Instant.ofEpochMilli(cursor).atZone(zone).hour
                val hourEndMillis = java.time.Instant.ofEpochMilli(cursor).atZone(zone)
                    .withMinute(0).withSecond(0).withNano(0)
                    .plusHours(1).toInstant().toEpochMilli()
                val segmentEnd = minOf(to, hourEndMillis)
                buckets[hour] += (segmentEnd - cursor).coerceAtLeast(0L)
                cursor = segmentEnd
            }
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val isResume = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (android.os.Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            val isPause = event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                (android.os.Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_PAUSED)

            when {
                isResume -> {
                    if (!openedAt.containsKey(pkg)) openedAt[pkg] = event.timeStamp
                    closedSincePause.remove(pkg)
                }
                isPause -> {
                    if (pkg !in closedSincePause) {
                        val start = openedAt.remove(pkg) ?: startMillis
                        val clippedStart = start.coerceIn(startMillis, endMillis)
                        val clippedEnd = event.timeStamp.coerceIn(startMillis, endMillis)
                        if (clippedEnd > clippedStart) creditInterval(clippedStart, clippedEnd)
                        closedSincePause.add(pkg)
                    }
                }
            }
        }
        openedAt.values.forEach { start ->
            val clippedStart = start.coerceIn(startMillis, endMillis)
            if (endMillis > clippedStart) creditInterval(clippedStart, endMillis)
        }
        return buckets.toList()
    }

    /**
     * App label + icon, filtering out only genuine background-only
     * components (no launcher entry AND a pure system component) —
     * never anything the user could actually have opened.
     *
     * Previous bug: this filtered out ANY app with FLAG_SYSTEM set that
     * lacked a launcher intent, on the assumption that meant "internal
     * service, not a real app". That assumption is wrong on real
     * devices: OEMs ship many apps users genuinely open — updated system
     * apps (Chrome, Gmail, Maps once updated via Play Store keep
     * FLAG_SYSTEM from their factory install), OEM camera/gallery/dialer
     * apps, carrier apps — all flagged FLAG_SYSTEM despite having a
     * completely normal launcher entry and real usage. On some OEM
     * skins getLaunchIntentForPackage() also unreliably returns null for
     * apps that *do* have a launcher icon (aliased/dynamic launcher
     * activities), which silently dropped those apps too along with
     * their entire foreground time — explaining "usage total doesn't
     * match sum of visible apps".
     *
     * Correct rule: we only ever reach this function for a package that
     * UsageStatsManager already reported real foreground time for — the
     * user visibly used it. So the only apps worth hiding here are ones
     * whose CATEGORY_LAUNCHER component genuinely doesn't exist ANYWHERE
     * in the package (checked directly via queryIntentActivities against
     * this specific package, not the single default-launcher lookup) —
     * i.e. truly headless system/service packages that could not have
     * been opened by a tap. Everything else — the fact that
     * UsageStatsManager saw it in the foreground at all — is proof
     * enough that it belongs on the list.
     */
    private fun resolveAppInfo(packageName: String): Pair<String, android.graphics.drawable.Drawable?>? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)

            val hasAnyLauncherActivity = packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName),
                0
            ).isNotEmpty()

            val isKnownHeadlessException = packageName == "android" || packageName == context.packageName
            if (!hasAnyLauncherActivity && !isKnownHeadlessException) return null

            val label = packageManager.getApplicationLabel(appInfo).toString()
            val icon = try { packageManager.getApplicationIcon(appInfo) } catch (_: Exception) { null }
            label to icon
        } catch (_: PackageManager.NameNotFoundException) {
            // Package was uninstalled after the usage event was recorded —
            // genuinely can't be shown (no icon/label source left), not a
            // filtering decision.
            null
        }
    }

    /**
     * Estimates the device's total battery percentage drop for a given
     * day using the app's own real, periodically-sampled drain_samples
     * table (~15min resolution, see DrainMonitorWorker) — the earliest
     * and latest samples that fall inside that day give a genuine
     * measured delta. Android exposes no public per-day historical
     * battery API to third-party apps, so this is the only real (not
     * fabricated) source available: if there are fewer than two samples
     * for the day (e.g. app was just installed, or the device was
     * charging the whole day so no drain samples were recorded), this
     * returns null and every batteryPercent built from it is also null
     * — the UI omits the figure rather than inventing one.
     */
    private suspend fun estimateBatteryDropForDay(dateEpochDay: Long, zone: ZoneId): Float? {
        val date = LocalDate.ofEpochDay(dateEpochDay)
        val startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = minOf(
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            System.currentTimeMillis()
        )
        val samples = drainSampleDao.getSince(startMillis).filter { it.timestampMillis < endMillis }
        if (samples.size < 2) return null
        val first = samples.minByOrNull { it.timestampMillis } ?: return null
        val last = samples.maxByOrNull { it.timestampMillis } ?: return null
        val drop = first.batteryPercent - last.batteryPercent
        return if (drop > 0) drop.toFloat() else null
    }
}
