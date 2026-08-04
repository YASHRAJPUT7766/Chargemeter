package com.yash.chargemeterpro.domain.usecase

import kotlin.math.roundToLong

/**
 * Estimates "how long the phone will last on the remaining battery" while
 * NOT charging, using the same measured %/hour drain rate that
 * [DrainRateCalculator] already produces from DrainSampleEntity rows
 * (collected roughly every 15 minutes by DrainMonitorWorker).
 *
 * Deliberately mirrors [ChargeTimeEstimator]'s philosophy: this is not an
 * OS-level estimate (no stable public API exposes one), it's derived
 * entirely from this device's OWN recently measured drain rate, so it's
 * transparent and self-correcting for that user's actual usage pattern
 * rather than a generic per-model guess. Returns null rather than a
 * fabricated number whenever there isn't yet a trustworthy rate — see
 * DrainRateCalculator's MIN_WINDOW_HOURS gate.
 */
object DischargeTimeEstimator {

    data class DischargeEstimate(
        val minutesRemaining: Long?,
        /** The measured %/hour drain rate this estimate is based on — surfaced in UI for transparency. */
        val percentPerHourRate: Double?
    )

    fun estimate(currentBatteryPercent: Int, drainRate: DrainRateResult): DischargeEstimate {
        val rate = drainRate.percentPerHour
        if (rate == null || rate <= 0.0) {
            return DischargeEstimate(minutesRemaining = null, percentPerHourRate = rate)
        }
        val hoursRemaining = currentBatteryPercent / rate
        val minutesRemaining = (hoursRemaining * 60.0).roundToLong().coerceAtLeast(0L)
        return DischargeEstimate(minutesRemaining = minutesRemaining, percentPerHourRate = rate)
    }
}
