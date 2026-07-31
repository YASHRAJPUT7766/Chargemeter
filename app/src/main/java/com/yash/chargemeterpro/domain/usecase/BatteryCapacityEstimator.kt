package com.yash.chargemeterpro.domain.usecase

import com.yash.chargemeterpro.domain.model.AvailableOr
import com.yash.chargemeterpro.domain.model.BatterySnapshot

/**
 * Best-effort estimation of the battery's CURRENT usable capacity (not
 * fabricated "design capacity in mAh" — most devices don't expose the
 * original factory-rated capacity through any public API at all).
 *
 * Method: if BATTERY_PROPERTY_CHARGE_COUNTER (remaining charge in µAh) is
 * available alongside battery percent, we can back-calculate an implied
 * "capacity at 100%" for THIS reading:
 *
 *     impliedFullCapacity = chargeCounter / (percent / 100)
 *
 * A SINGLE reading of this is noisy and not very meaningful. What's
 * actually useful is comparing this implied full-capacity figure across
 * MANY readings over time (weeks/months) — if it trends downward, that's
 * a real signal of capacity fade. This class only computes the
 * single-reading implied capacity; SettingsRepository/HistoryRepository
 * is responsible for persisting a rolling baseline (first-ever reading,
 * or a trailing-30-day median) so BatteryHealthScorer can compute a fade
 * percentage from a genuine before/after comparison rather than a single
 * noisy sample.
 *
 * On devices where CHARGE_COUNTER isn't exposed (common — this is one of
 * the less consistently implemented BatteryManager properties), this
 * entire capability is simply unavailable, and every function here
 * returns Unavailable rather than a guess.
 */
object BatteryCapacityEstimator {

    /** Implied full-charge capacity in µAh for a single reading, or Unavailable. */
    fun impliedFullCapacityMicroAh(snapshot: BatterySnapshot): AvailableOr<Long> {
        val chargeCounter = snapshot.chargeCounterMicroAh.orNull() ?: return AvailableOr.Unavailable
        val percent = snapshot.batteryPercent
        if (percent <= 0) return AvailableOr.Unavailable // avoid division by ~0 producing a wild extrapolation
        val implied = (chargeCounter.toDouble() / (percent / 100.0)).toLong()
        return AvailableOr.Value(implied)
    }

    /**
     * Capacity fade percentage = how much smaller the current implied
     * full capacity is versus a [baselineFullCapacityMicroAh] recorded
     * earlier (e.g. the first reading this app ever took on this device,
     * stored once in DataStore and never overwritten). Requires BOTH a
     * current reading and a stored baseline — if either is missing,
     * returns Unavailable rather than assuming a baseline of "the spec
     * capacity", which we don't reliably know either.
     */
    fun capacityFadePercent(
        currentImpliedCapacity: AvailableOr<Long>,
        baselineFullCapacityMicroAh: Long?
    ): AvailableOr<Double> {
        val current = currentImpliedCapacity.orNull() ?: return AvailableOr.Unavailable
        val baseline = baselineFullCapacityMicroAh ?: return AvailableOr.Unavailable
        if (baseline <= 0) return AvailableOr.Unavailable
        val fade = ((baseline - current).toDouble() / baseline.toDouble()) * 100.0
        // Clamp to a sane range — a single noisy reading occasionally
        // implies capacity *higher* than baseline, which we treat as ~0
        // fade rather than "negative fade", and cap the top end since a
        // wildly implausible fade number from a bad reading is more
        // likely sensor noise than reality.
        return AvailableOr.Value(fade.coerceIn(0.0, 60.0))
    }
}
