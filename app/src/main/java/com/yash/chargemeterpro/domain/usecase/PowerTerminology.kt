package com.yash.chargemeterpro.domain.usecase

/**
 * Single source of truth for the exact wording used anywhere the app
 * talks about power measurement limitations. Centralizing this means the
 * disclaimer text can't drift out of sync between the Home dashboard,
 * Live Monitor, Charger Analysis, and Efficiency screens — and it makes
 * it easy to audit that we are never overstating what's measured.
 *
 * These correspond 1:1 to the required disclaimer strings in
 * res/values/strings.xml (disclaimer_wattage_estimate, disclaimer_wall_output,
 * disclaimer_efficiency) — kept as Kotlin constants too so non-Compose
 * contexts (widget RemoteViews, notification text, PDF/CSV export) can
 * reference the identical wording without inflating a string resource ID
 * through every layer.
 */
object PowerTerminology {

    const val BATTERY_INPUT_POWER_LABEL = "Battery Input Power"
    const val WALL_OUTPUT_POWER_LABEL = "Charger / Wall Output Power"

    const val WATTAGE_ESTIMATE_DISCLAIMER =
        "Displayed wattage is an estimate of power delivered to the battery, " +
            "calculated from the phone's reported voltage and current. It may differ " +
            "from the charger's actual wall-side output due to charging losses and " +
            "device-specific reporting limitations."

    const val WALL_OUTPUT_UNAVAILABLE_DISCLAIMER =
        "Exact charger output cannot be directly measured by this device. The " +
            "displayed charging power is based on the phone's reported battery " +
            "voltage and charging current, not the charger itself."

    const val EFFICIENCY_DISCLAIMER =
        "True wall-to-battery efficiency requires measuring power drawn from the " +
            "wall outlet, which this device cannot do. The figure shown is an " +
            "estimate of energy delivered to the battery only."

    const val HEALTH_SCORE_DISCLAIMER =
        "This health score is a rough indicator based on the limited battery data " +
            "this device exposes. It is not a substitute for a professional battery " +
            "diagnostic."

    const val NOT_AVAILABLE = "Not available on this device"
}
