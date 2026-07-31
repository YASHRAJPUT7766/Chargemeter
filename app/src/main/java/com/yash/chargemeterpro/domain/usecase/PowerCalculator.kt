package com.yash.chargemeterpro.domain.usecase

import com.yash.chargemeterpro.domain.model.BatterySnapshot

/**
 * All power/energy math for the app lives here, in one place, so unit
 * conversions can't drift between screens. Every function is a pure,
 * side-effect-free calculation over already-normalized SI-friendly units.
 *
 * Core formula (per spec): Power (W) = Voltage (V) x Current (A)
 *
 * IMPORTANT — what this number IS and ISN'T:
 * This calculates "Battery Input Power": the power the device's own
 * fuel-gauge hardware reports as flowing into the battery, based on the
 * *battery-side* voltage and current the OS exposes. It is NOT and cannot
 * be "Charger/Wall Output Power" — the power actually being drawn from
 * the wall outlet — because that would require measuring the AC/USB input
 * side directly, upstream of the phone's charging circuitry, which no
 * public Android API exposes. The gap between the two is charging-circuit
 * conversion loss (heat), and it is real but not measurable from
 * software alone. See PowerTerminology.kt for the exact user-facing
 * strings that keep this distinction visible everywhere in the UI.
 */
object PowerCalculator {

    /**
     * Battery input power in Watts, or null if either input is
     * unavailable. Never returns 0.0 as a stand-in for "unknown" — a
     * genuine 0W reading (e.g. fully charged, trickle current at exactly
     * zero) is a legitimate value distinct from "we don't know".
     */
    fun batteryInputPowerWatts(voltageVolts: Double?, currentAmps: Double?): Double? {
        if (voltageVolts == null || currentAmps == null) return null
        if (voltageVolts <= 0.0) return null // a non-positive voltage reading is not physically meaningful here
        return voltageVolts * currentAmps
    }

    fun batteryInputPowerWatts(snapshot: BatterySnapshot): Double? =
        batteryInputPowerWatts(snapshot.voltageVolts, snapshot.currentAmpsNormalized)

    /**
     * Energy in watt-hours transferred over [durationHours] at a given
     * average power. Used by the session tracker to turn a stream of
     * power samples into a cumulative "estimated energy transferred"
     * figure. Trapezoidal integration is applied by
     * SessionEnergyIntegrator over a full sample series — this function
     * is the single-interval building block.
     */
    fun energyWattHours(averagePowerWatts: Double, durationHours: Double): Double {
        if (durationHours <= 0.0) return 0.0
        return averagePowerWatts * durationHours
    }

    /** Convenience: converts milliamps to amps. */
    fun mAToA(milliAmps: Double): Double = milliAmps / 1000.0

    /** Convenience: converts millivolts to volts. */
    fun mVToV(milliVolts: Int): Double = milliVolts / 1000.0
}
