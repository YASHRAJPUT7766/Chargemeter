package com.yash.chargemeterpro.domain.model

/**
 * A single point-in-time reading of everything we could retrieve from
 * Android's battery APIs. Every field is nullable/Unavailable-aware on
 * purpose — see AvailableOr<T> below. Nothing in this model is ever
 * fabricated: if the OS/hardware doesn't expose it, the field is
 * [AvailableOr.Unavailable] and the UI is required to render
 * "Not available on this device" rather than a placeholder number.
 *
 * Sources (see BatteryDataSource.kt for exact API calls):
 *  - Sticky broadcast Intent.ACTION_BATTERY_CHANGED (percentage, voltage,
 *    temperature, health, plugged type, technology, present)
 *  - BatteryManager system service (charging current via
 *    BATTERY_PROPERTY_CURRENT_NOW, charge counter, capacity %, status,
 *    charging policy on API 34+)
 */
data class BatterySnapshot(
    val timestampMillis: Long,

    /** 0-100. Always available — comes from EXTRA_LEVEL/EXTRA_SCALE, present on every device. */
    val batteryPercent: Int,

    /** From BatteryManager.BATTERY_STATUS_*. Always available. */
    val chargingStatus: ChargingStatus,

    /** From EXTRA_PLUGGED: AC / USB / Wireless / Dock / none. Always available (0 = unplugged). */
    val plugType: PlugType,

    /**
     * Battery voltage in millivolts, from EXTRA_VOLTAGE. Almost universally
     * available and reliable — this is one of the most trustworthy fields
     * across OEMs.
     */
    val voltageMilliVolts: AvailableOr<Int>,

    /**
     * Instantaneous battery current in microamps, from
     * BatteryManager.BATTERY_PROPERTY_CURRENT_NOW. THE SIGN CONVENTION IS
     * NOT STANDARDIZED ACROSS OEMS:
     *   - Most (AOSP-conformant) devices report POSITIVE current while
     *     charging and NEGATIVE while discharging.
     *   - A meaningful minority of OEMs (notably some Samsung/older
     *     devices) invert this.
     * We normalize this at the repository layer using chargingStatus as
     * ground truth (see CurrentSignNormalizer) rather than trusting the
     * raw sign blindly — see that file for the exact heuristic and its
     * documented limitation.
     */
    val currentMicroAmps: AvailableOr<Long>,

    /** °C tenths from EXTRA_TEMPERATURE (e.g. 320 = 32.0°C). Almost always available. */
    val temperatureCelsius: AvailableOr<Double>,

    /** From EXTRA_HEALTH. Often available, but many devices only ever report GOOD. */
    val health: BatteryHealth,

    /** From EXTRA_TECHNOLOGY, e.g. "Li-ion", "Li-poly". Usually available. */
    val technology: AvailableOr<String>,

    /**
     * Design capacity in µAh via BATTERY_PROPERTY_CHARGE_COUNTER combined
     * with EXTRA_LEVEL, OR total capacity if the OEM exposes it through
     * PowerProfile reflection (best-effort, see BatteryCapacityEstimator).
     * Frequently unavailable — many devices simply don't expose this.
     */
    val chargeCounterMicroAh: AvailableOr<Long>,

    /**
     * Battery capacity as % of design capacity, from
     * BATTERY_PROPERTY_CAPACITY. This is charge level (same as
     * batteryPercent in most cases), NOT design health capacity — kept
     * separate because on a few devices these values diverge slightly and
     * conflating them would be misleading.
     */
    val capacityPercent: AvailableOr<Int>,

    /**
     * Cycle count from EXTRA_CYCLE_COUNT (API 34+ only) or, on older API
     * levels, we do not attempt any estimate — there is no reliable public
     * API for this pre-Android 14 and we will not fabricate a number.
     */
    val cycleCount: AvailableOr<Int>,

    /** True once EXTRA_CHARGING_POLICY reports adaptive/optimized charging is active (API 34+). */
    val chargingPolicy: AvailableOr<ChargingPolicy> = AvailableOr.Unavailable,

    /** Whether the OS reports a battery is present at all (should always be true on phones). */
    val batteryPresent: Boolean = true,

    /** From EXTRA_MAX_CHARGING_CURRENT — the platform's OWN estimate of max current, when exposed. */
    val maxChargingCurrentMicroAmps: AvailableOr<Long> = AvailableOr.Unavailable,

    /** From EXTRA_MAX_CHARGING_VOLTAGE — platform's own max voltage estimate, when exposed. */
    val maxChargingVoltageMicroVolts: AvailableOr<Long> = AvailableOr.Unavailable,

    /** USB_STATE broadcast derived: is a USB data/power connection currently present. */
    val usbConnected: Boolean = false,

    /** From UsbManager / USB_STATE extras where obtainable: is USB fast-charge negotiated. */
    val usbFastChargeDetected: AvailableOr<Boolean> = AvailableOr.Unavailable
) {
    /**
     * Voltage in Volts (not millivolts) — convenience for display & power calc.
     * Returns null when the source field is unavailable.
     */
    val voltageVolts: Double?
        get() = (voltageMilliVolts as? AvailableOr.Value)?.value?.let { it / 1000.0 }

    /**
     * Current in Amps (not microamps), sign-normalized so POSITIVE always
     * means "flowing into the battery" (i.e., charging) regardless of the
     * raw OEM sign convention. See CurrentSignNormalizer for how this is
     * derived from the raw currentMicroAmps.
     */
    val currentAmpsNormalized: Double?
        get() = (currentMicroAmps as? AvailableOr.Value)?.value?.let { it / 1_000_000.0 }

    val currentMilliAmpsNormalized: Double?
        get() = (currentMicroAmps as? AvailableOr.Value)?.value?.let { it / 1000.0 }

    val temperatureC: Double?
        get() = (temperatureCelsius as? AvailableOr.Value)?.value

    /**
     * Battery input power in Watts = V x I, i.e. the power estimated to be
     * going INTO the battery based on device-reported values. This is
     * explicitly NOT charger/wall output — see PowerCalculator.kt and the
     * disclaimer strings shown alongside every wattage display in the UI.
     * Returns null (not zero, not a guess) if either voltage or current is
     * unavailable.
     */
    val batteryInputPowerWatts: Double?
        get() {
            val v = voltageVolts ?: return null
            val i = currentAmpsNormalized ?: return null
            return v * i
        }

    val isCharging: Boolean
        get() = chargingStatus == ChargingStatus.CHARGING

    val isFull: Boolean
        get() = chargingStatus == ChargingStatus.FULL || batteryPercent >= 100
}

/** Wraps any value that Android may or may not expose on a given device. */
sealed class AvailableOr<out T> {
    data class Value<T>(val value: T) : AvailableOr<T>()
    data object Unavailable : AvailableOr<Nothing>()

    inline fun <R> map(transform: (T) -> R): AvailableOr<R> = when (this) {
        is Value -> Value(transform(value))
        Unavailable -> Unavailable
    }

    fun orNull(): T? = (this as? Value)?.value
    val isAvailable: Boolean get() = this is Value
}

enum class ChargingStatus {
    CHARGING, DISCHARGING, NOT_CHARGING, FULL, UNKNOWN
}

enum class PlugType {
    AC, USB, WIRELESS, DOCK, NONE
}

enum class BatteryHealth {
    GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, UNSPECIFIED_FAILURE, COLD, UNKNOWN
}

enum class ChargingPolicy {
    DEFAULT, ADAPTIVE_LONGEVITY, UNKNOWN
}
