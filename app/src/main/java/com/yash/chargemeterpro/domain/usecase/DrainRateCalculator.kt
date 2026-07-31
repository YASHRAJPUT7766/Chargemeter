package com.yash.chargemeterpro.domain.usecase

import com.yash.chargemeterpro.data.local.entity.DrainSampleEntity

data class DrainRateResult(
    val percentPerHour: Double?,
    val sampleCount: Int,
    val windowHours: Double
)

/**
 * Computes battery drain rate (%/hour) from a series of [DrainSampleEntity]
 * rows collected by DrainMonitorWorker. Requires at least 2 samples
 * spanning a meaningful window — with the 15-minute worker cadence, this
 * means a genuinely useful rate typically needs at least a couple of
 * worker runs (~30min+) of data, so this returns null rather than an
 * unstable single-pair rate when the window is too short.
 */
object DrainRateCalculator {

    private const val MIN_WINDOW_HOURS = 0.4 // ~24 minutes — roughly 2 worker cadences

    fun calculate(samplesOldestToNewest: List<DrainSampleEntity>): DrainRateResult {
        if (samplesOldestToNewest.size < 2) {
            return DrainRateResult(null, samplesOldestToNewest.size, 0.0)
        }
        val oldest = samplesOldestToNewest.first()
        val newest = samplesOldestToNewest.last()
        val windowHours = (newest.timestampMillis - oldest.timestampMillis) / 3_600_000.0

        if (windowHours < MIN_WINDOW_HOURS) {
            return DrainRateResult(null, samplesOldestToNewest.size, windowHours)
        }

        val percentDropped = oldest.batteryPercent - newest.batteryPercent
        // Percent may legitimately not have dropped at all in a short
        // window (e.g. screen off, idle) — that's a real 0-ish rate, not
        // missing data, so we still return a value (which may be ~0 or
        // even slightly negative from measurement noise, in which case we
        // floor it at 0 since "charging while not marked as charging" is
        // not a state we display a negative drain rate for).
        val ratePerHour = (percentDropped / windowHours).coerceAtLeast(0.0)

        return DrainRateResult(ratePerHour, samplesOldestToNewest.size, windowHours)
    }
}
