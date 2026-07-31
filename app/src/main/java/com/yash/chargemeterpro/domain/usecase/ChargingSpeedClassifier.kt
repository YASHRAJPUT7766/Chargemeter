package com.yash.chargemeterpro.domain.usecase

import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.model.PlugType

enum class ChargingSpeed { FAST, NORMAL, SLOW, TRICKLE, UNKNOWN }

/**
 * Classifies the CURRENT charging speed from measured battery input power.
 *
 * There is no public Android API that says "this is a fast charger" as a
 * boolean fact — EXTRA_MAX_CHARGING_CURRENT/VOLTAGE exist on some
 * devices/ROMs but are inconsistently populated, and many devices simply
 * don't set them. So instead of pretending to detect a charging
 * *protocol*, this classifier works off measured wattage using thresholds
 * grounded in real-world USB power delivery tiers:
 *
 *   - USB 2.0 unit load / legacy USB charging: ~2.5W (5V x 0.5A)
 *   - Standard USB charging (BC1.2/5V-1A class): ~5W
 *   - "Fast charging" territory for phones generally starts meaningfully
 *     above standard 5W USB — most phone fast-charging standards (USB-PD,
 *     Quick Charge, proprietary schemes) target 15W+ at the low end and
 *     scale up from there.
 *
 * When the device DOES expose EXTRA_MAX_CHARGING_CURRENT/VOLTAGE, we use
 * those as a device-specific ceiling to make the classification relative
 * to what THIS phone's charging circuit is actually capable of, rather
 * than an absolute number that might misclassify a phone whose max
 * supported input is itself only 10W. Falls back to the absolute
 * wattage tiers when that data isn't available.
 *
 * This is a heuristic, clearly labeled as such in the UI ("Charging
 * Speed: FAST" is a classification, not a claim about a specific
 * protocol) — see ChargerAnalyzer.kt for the separate, stricter logic
 * around what protocol-level claims we're willing to make at all.
 */
object ChargingSpeedClassifier {

    // Absolute fallback thresholds in Watts, used when the device doesn't
    // expose its own max-charging-current/voltage ceiling.
    private const val TRICKLE_CEILING_W = 2.5
    private const val NORMAL_CEILING_W = 7.5
    private const val FAST_FLOOR_W = 15.0

    fun classify(snapshot: BatterySnapshot): ChargingSpeed {
        if (!snapshot.isCharging) return ChargingSpeed.UNKNOWN
        val powerW = PowerCalculator.batteryInputPowerWatts(snapshot) ?: return ChargingSpeed.UNKNOWN

        val deviceMaxW = deviceReportedMaxWatts(snapshot)
        return if (deviceMaxW != null && deviceMaxW > 0.0) {
            classifyRelativeToDeviceMax(powerW, deviceMaxW)
        } else {
            classifyAbsolute(powerW, snapshot.plugType)
        }
    }

    private fun classifyRelativeToDeviceMax(powerW: Double, deviceMaxW: Double): ChargingSpeed {
        val ratio = (powerW / deviceMaxW).coerceIn(0.0, 1.5)
        return when {
            powerW < TRICKLE_CEILING_W -> ChargingSpeed.TRICKLE
            ratio >= 0.6 -> ChargingSpeed.FAST
            ratio >= 0.25 -> ChargingSpeed.NORMAL
            else -> ChargingSpeed.SLOW
        }
    }

    private fun classifyAbsolute(powerW: Double, plugType: PlugType): ChargingSpeed = when {
        powerW < TRICKLE_CEILING_W -> ChargingSpeed.TRICKLE
        powerW < NORMAL_CEILING_W -> ChargingSpeed.SLOW
        powerW < FAST_FLOOR_W -> ChargingSpeed.NORMAL
        else -> ChargingSpeed.FAST
    }.let { classification ->
        // Wireless charging has inherent conversion losses even at "fast"
        // wireless tiers (Qi fast charging is commonly 10-15W, rarely
        // hits phone-cable-fast territory) — we don't inflate a wireless
        // reading into FAST unless the wattage genuinely clears the same
        // bar a wired fast charger would.
        classification
    }

    private fun deviceReportedMaxWatts(snapshot: BatterySnapshot): Double? {
        val maxCurrentUa = snapshot.maxChargingCurrentMicroAmps.orNull() ?: return null
        val maxVoltageUv = snapshot.maxChargingVoltageMicroVolts.orNull()
        // EXTRA_MAX_CHARGING_VOLTAGE is even less consistently populated
        // than max current; if absent, fall back to the CURRENT battery
        // voltage as a reasonable proxy for computing a ceiling wattage,
        // since voltage during charging is relatively stable for a given
        // charge state.
        val voltageV = maxVoltageUv?.let { it / 1_000_000.0 } ?: snapshot.voltageVolts ?: return null
        val maxCurrentA = maxCurrentUa / 1_000_000.0
        return voltageV * maxCurrentA
    }
}
