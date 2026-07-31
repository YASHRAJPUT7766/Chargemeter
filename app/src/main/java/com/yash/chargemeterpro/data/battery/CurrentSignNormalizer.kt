package com.yash.chargemeterpro.data.battery

import com.yash.chargemeterpro.domain.model.AvailableOr
import com.yash.chargemeterpro.domain.model.ChargingStatus

/**
 * BatteryManager.BATTERY_PROPERTY_CURRENT_NOW's sign convention is NOT
 * guaranteed by the Android platform docs beyond "positive values
 * indicate net current entering the battery, negative values indicate net
 * discharging" — which is the AOSP-reference-HAL behavior. In practice,
 * a meaningful number of OEM HAL implementations invert this, reporting
 * negative-while-charging instead. There is no public API to ask "which
 * convention does this device use."
 *
 * We handle this with a conservative, documented heuristic rather than
 * silently trusting either convention:
 *
 *   1. Use BatteryManager's own EXTRA_STATUS (CHARGING/DISCHARGING) as
 *      ground truth for the *direction* of current flow — this field is
 *      far more consistently implemented across OEMs than the current
 *      sign is.
 *   2. If the reported charging status is CHARGING but the raw current
 *      sign says "discharging" (negative, under the AOSP convention), we
 *      flip the sign so the normalized value is positive — matching our
 *      documented convention that positive = flowing into the battery.
 *   3. Same logic in reverse for DISCHARGING status.
 *   4. If status is NOT_CHARGING, FULL, or UNKNOWN, we do not flip
 *      anything — we pass the raw value through, since "ground truth"
 *      direction is ambiguous in those states anyway (current should be
 *      near zero or trickle).
 *
 * This heuristic is NOT foolproof — it assumes EXTRA_STATUS itself is
 * correctly implemented, which is a safer assumption than assuming a
 * consistent current sign, but still an assumption. We do not present
 * the *sign-normalized* value as more authoritative than it is anywhere
 * in the UI copy; we simply use it consistently everywhere so numbers
 * don't flip-flop confusingly between screens.
 */
object CurrentSignNormalizer {

    /**
     * Returns a sign-normalized copy of [rawMicroAmps] such that positive
     * always means "current flowing into the battery" for the given
     * [status]. Returns [AvailableOr.Unavailable] unchanged if input was
     * unavailable.
     */
    fun normalize(
        rawMicroAmps: AvailableOr<Long>,
        status: ChargingStatus
    ): AvailableOr<Long> {
        val raw = (rawMicroAmps as? AvailableOr.Value)?.value ?: return AvailableOr.Unavailable

        return when (status) {
            ChargingStatus.CHARGING -> {
                // Ground truth says current SHOULD be flowing in (positive
                // under our convention). If raw is negative, this device's
                // HAL uses the inverted convention — flip it.
                AvailableOr.Value(if (raw < 0) -raw else raw)
            }
            ChargingStatus.DISCHARGING -> {
                // Ground truth says current SHOULD be flowing out
                // (negative under our convention). If raw is positive,
                // flip it.
                AvailableOr.Value(if (raw > 0) -raw else raw)
            }
            ChargingStatus.FULL, ChargingStatus.NOT_CHARGING, ChargingStatus.UNKNOWN -> {
                // Ambiguous ground truth — pass through unmodified rather
                // than guess a flip direction.
                AvailableOr.Value(raw)
            }
        }
    }
}
