package com.yash.chargemeterpro.domain.usecase

import com.yash.chargemeterpro.domain.model.AvailableOr
import com.yash.chargemeterpro.domain.model.BatteryHealth
import com.yash.chargemeterpro.domain.model.BatterySnapshot

/**
 * Produces a coarse 0-100 "Battery Health Score", but ONLY when there is
 * real signal to base it on. Per the product requirement ("Do not invent
 * or falsely estimate health data"), this deliberately returns
 * [HealthScoreResult.InsufficientData] — not a fabricated middling score
 * like "75" — whenever the inputs it needs aren't available.
 *
 * What genuinely contributes to the score, and why each is treated as
 * weak/strong evidence:
 *
 *  1. EXTRA_HEALTH (BatteryHealth enum) — STRONG signal when it reports
 *     anything other than GOOD/UNKNOWN (e.g. OVERHEAT, OVER_VOLTAGE are
 *     explicit hardware-reported problems). However, many devices *only*
 *     ever report GOOD regardless of actual wear, so GOOD alone is only
 *     WEAK positive evidence, not proof of a healthy battery.
 *
 *  2. Cycle count (API 34+ only) — MODERATE signal when available: high
 *     cycle counts correlate with capacity fade in lithium-ion cells in
 *     general, though the exact relationship is chemistry/design
 *     specific and we do not pretend to model it precisely.
 *
 *  3. Charge counter vs battery percent (BatteryCapacityEstimator) —
 *     MODERATE signal when available on both current AND a historical
 *     baseline, since a single reading alone can't establish fade without
 *     knowing the original design capacity, which most devices don't
 *     expose either.
 *
 * If NEITHER cycle count NOR a usable capacity estimate is available —
 * which will be the common case on many devices below API 34 — we simply
 * do not compute a numeric score at all, and the UI is required to show
 * an explanatory message instead of any number.
 */
object BatteryHealthScorer {

    sealed class HealthScoreResult {
        data class Score(
            val value: Int, // 0-100
            val basedOn: List<String> // human-readable list of what fed into it, shown in UI for transparency
        ) : HealthScoreResult()

        data class InsufficientData(val reason: String) : HealthScoreResult()
    }

    fun score(
        snapshot: BatterySnapshot,
        estimatedCapacityFadePercent: AvailableOr<Double> // from BatteryCapacityEstimator, when derivable
    ): HealthScoreResult {
        val hasCycleCount = snapshot.cycleCount.isAvailable
        val hasCapacityFade = estimatedCapacityFadePercent.isAvailable
        val explicitHealthProblem = snapshot.health !in setOf(BatteryHealth.GOOD, BatteryHealth.UNKNOWN)

        if (!hasCycleCount && !hasCapacityFade && !explicitHealthProblem) {
            return HealthScoreResult.InsufficientData(
                "This device does not expose battery cycle count or enough historical " +
                    "data to estimate capacity fade. A reliable health score cannot be " +
                    "calculated — showing raw battery diagnostics instead."
            )
        }

        var points = 100.0
        val basedOn = mutableListOf<String>()

        if (explicitHealthProblem) {
            points -= 40.0
            basedOn.add("Battery health flag: ${snapshot.health.name}")
        }

        (snapshot.cycleCount as? AvailableOr.Value)?.value?.let { cycles ->
            // Rough, clearly-approximate deduction curve: most phone Li-ion
            // cells are commonly rated for several hundred cycles to ~80%
            // of original capacity. We deduct gradually rather than
            // asserting a precise remaining-capacity number we can't back up.
            val deduction = when {
                cycles < 200 -> 0.0
                cycles < 400 -> 5.0
                cycles < 600 -> 12.0
                cycles < 800 -> 20.0
                else -> 30.0
            }
            points -= deduction
            basedOn.add("Cycle count: $cycles")
        }

        (estimatedCapacityFadePercent as? AvailableOr.Value)?.value?.let { fadePercent ->
            points -= fadePercent.coerceIn(0.0, 40.0)
            basedOn.add("Estimated capacity fade: ${"%.1f".format(fadePercent)}%")
        }

        val clamped = points.coerceIn(0.0, 100.0).toInt()
        return HealthScoreResult.Score(clamped, basedOn)
    }
}
