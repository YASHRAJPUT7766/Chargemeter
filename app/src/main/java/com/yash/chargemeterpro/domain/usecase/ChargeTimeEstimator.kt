package com.yash.chargemeterpro.domain.usecase

import com.yash.chargemeterpro.domain.model.AvailableOr
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Estimates remaining charging time and time-to-milestone (80/90/100%).
 *
 * IMPORTANT — this is deliberately NOT using any "look at an OS-level
 * estimate" shortcut, because no stable public API exposes one (the
 * closest, BatteryStatsManager, requires a system/signature permission
 * this app cannot hold). Instead we estimate from OUR OWN recent measured
 * charge rate (percent-per-minute over a trailing window), which is:
 *   - Transparent: the user can see exactly what it's based on.
 *   - Self-correcting: it naturally accounts for charging curve tapering
 *     near 100% because the trailing window's rate slows down too.
 *   - Honest about uncertainty: returns null/Unavailable until enough
 *     samples exist to compute a rate, rather than showing a number
 *     confidently from sample 1.
 */
object ChargeTimeEstimator {

    /** Need at least this many minutes of trailing data before estimating, to avoid noisy single-sample rates. */
    private const val MIN_WINDOW_MINUTES = 2.0
    private const val MIN_PERCENT_DELTA_FOR_RATE = 1

    data class RateSample(val timestampMillis: Long, val batteryPercent: Int)

    data class TimeEstimate(
        val minutesRemainingToFull: Long?,
        val minutesTo80: Long?,
        val minutesTo90: Long?,
        val minutesTo100: Long?,
        /** The measured %/minute rate this estimate is based on — shown in UI for transparency. */
        val percentPerMinuteRate: Double?
    )

    /**
     * [recentSamples] should be ordered oldest-to-newest, ideally spanning
     * the last 5-10 minutes of the current session. Callers (the live
     * monitor ViewModel / charging session tracker) maintain this window;
     * this function is a pure calculation over whatever window it's given.
     */
    fun estimate(currentSnapshot: BatterySnapshot, recentSamples: List<RateSample>): TimeEstimate {
        if (!currentSnapshot.isCharging || currentSnapshot.isFull) {
            return TimeEstimate(null, null, null, null, null)
        }
        if (recentSamples.size < 2) {
            return TimeEstimate(null, null, null, null, null)
        }

        val oldest = recentSamples.first()
        val newest = recentSamples.last()
        val windowMinutes = (newest.timestampMillis - oldest.timestampMillis) / 60_000.0
        val percentDelta = newest.batteryPercent - oldest.batteryPercent

        if (windowMinutes < MIN_WINDOW_MINUTES || percentDelta < MIN_PERCENT_DELTA_FOR_RATE) {
            // Not enough signal yet to trust a rate — e.g. charging just
            // started, or percent hasn't ticked over yet this session.
            return TimeEstimate(null, null, null, null, null)
        }

        val ratePercentPerMinute = percentDelta / windowMinutes
        if (ratePercentPerMinute <= 0.0) {
            return TimeEstimate(null, null, null, null, ratePercentPerMinute)
        }

        val currentPercent = currentSnapshot.batteryPercent

        fun minutesTo(target: Int): Long? {
            if (currentPercent >= target) return 0L
            val minutes = (target - currentPercent) / ratePercentPerMinute
            return max(0.0, minutes).roundToLong()
        }

        return TimeEstimate(
            minutesRemainingToFull = minutesTo(100),
            minutesTo80 = minutesTo(80),
            minutesTo90 = minutesTo(90),
            minutesTo100 = minutesTo(100),
            percentPerMinuteRate = ratePercentPerMinute
        )
    }

    fun Long?.asAvailableOr(): AvailableOr<Long> =
        this?.let { AvailableOr.Value(it) } ?: AvailableOr.Unavailable
}
