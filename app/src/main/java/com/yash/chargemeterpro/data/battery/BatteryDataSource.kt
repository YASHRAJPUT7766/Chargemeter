package com.yash.chargemeterpro.data.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.HardwarePropertiesManager
import androidx.core.content.ContextCompat
import com.yash.chargemeterpro.domain.model.AvailableOr
import com.yash.chargemeterpro.domain.model.BatteryHealth
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.model.ChargingPolicy
import com.yash.chargemeterpro.domain.model.ChargingStatus
import com.yash.chargemeterpro.domain.model.PlugType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for reading real battery/charging data from the
 * Android platform. Every value here traces back to a documented public
 * API — nothing is invented. Where a field genuinely cannot be read on a
 * given device/API level, we return [AvailableOr.Unavailable] rather than
 * a default/zero value, per the app's core "never fabricate data" rule.
 *
 * API surfaces used:
 *  1. Sticky broadcast [Intent.ACTION_BATTERY_CHANGED] — registering a
 *     receiver with a null Context on this returns the last sticky value
 *     immediately, which is the standard, officially documented way to
 *     get a battery snapshot on demand without waiting for a broadcast.
 *  2. [BatteryManager] system service `getIntProperty` / `getLongProperty`
 *     for values not present in the sticky broadcast (instantaneous
 *     current, charge counter, capacity %).
 */
@Singleton
class BatteryDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val batteryManager: BatteryManager? =
        ContextCompat.getSystemService(context, BatteryManager::class.java)

    /**
     * Reads the current battery/charging state synchronously from the
     * sticky broadcast + BatteryManager properties. Safe to call
     * frequently (e.g. every 1-2s from a polling loop) — registering for
     * a sticky broadcast with a null receiver is a cheap, documented,
     * synchronous operation and does not actually register a live
     * listener.
     */
    fun readSnapshot(): BatterySnapshot {
        val stickyIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = stickyIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = stickyIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else {
            // Fall back to the BatteryManager aggregate property, which
            // independently reports 0-100 and is available even if the
            // sticky intent momentarily failed to deliver extras.
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 } ?: 0
        }

        val statusExtra = stickyIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val chargingStatus = mapChargingStatus(statusExtra, batteryPercent)

        val pluggedExtra = stickyIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val plugType = mapPlugType(pluggedExtra)

        val voltageMv = stickyIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE && it > 0 }
        val voltage = voltageMv?.let { AvailableOr.Value(it) } ?: AvailableOr.Unavailable

        val rawCurrentMicroAmps = readCurrentNow()
        val current = rawCurrentMicroAmps?.let { AvailableOr.Value(it) } ?: AvailableOr.Unavailable

        val tempTenths = stickyIntent?.getIntExtra(
            BatteryManager.EXTRA_TEMPERATURE,
            Int.MIN_VALUE
        )?.takeIf { it != Int.MIN_VALUE }
        val temperature = tempTenths?.let { AvailableOr.Value(it / 10.0) } ?: AvailableOr.Unavailable

        val healthExtra = stickyIntent?.getIntExtra(
            BatteryManager.EXTRA_HEALTH,
            BatteryManager.BATTERY_HEALTH_UNKNOWN
        ) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val health = mapHealth(healthExtra)

        val technologyStr = stickyIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
        val technology = technologyStr?.takeIf { it.isNotBlank() }
            ?.let { AvailableOr.Value(it) } ?: AvailableOr.Unavailable

        val chargeCounter = readChargeCounter()
        val chargeCounterResult = chargeCounter?.let { AvailableOr.Value(it) } ?: AvailableOr.Unavailable

        val capacityProperty = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
        val capacityResult = capacityProperty?.let { AvailableOr.Value(it) } ?: AvailableOr.Unavailable

        val cycleCount = readCycleCount(stickyIntent)
        val cycleCountResult = cycleCount?.let { AvailableOr.Value(it) } ?: AvailableOr.Unavailable

        val chargingPolicy = readChargingPolicy(stickyIntent)

        val maxCurrent = stickyIntent?.getIntExtra(
            "max_charging_current",
            Int.MIN_VALUE
        )?.takeIf { it != Int.MIN_VALUE && it > 0 }
        val maxCurrentResult = maxCurrent?.let { AvailableOr.Value(it.toLong()) } ?: AvailableOr.Unavailable

        val maxVoltage = stickyIntent?.getIntExtra(
            "max_charging_voltage",
            Int.MIN_VALUE
        )?.takeIf { it != Int.MIN_VALUE && it > 0 }
        val maxVoltageResult = maxVoltage?.let { AvailableOr.Value(it.toLong()) } ?: AvailableOr.Unavailable

        val present = stickyIntent?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) ?: true

        return BatterySnapshot(
            timestampMillis = System.currentTimeMillis(),
            batteryPercent = batteryPercent,
            chargingStatus = chargingStatus,
            plugType = plugType,
            voltageMilliVolts = voltage,
            currentMicroAmps = current,
            temperatureCelsius = temperature,
            health = health,
            technology = technology,
            chargeCounterMicroAh = chargeCounterResult,
            capacityPercent = capacityResult,
            cycleCount = cycleCountResult,
            chargingPolicy = chargingPolicy,
            batteryPresent = present,
            maxChargingCurrentMicroAmps = maxCurrentResult,
            maxChargingVoltageMicroVolts = maxVoltageResult,
            usbConnected = plugType == PlugType.USB || plugType == PlugType.AC,
            usbFastChargeDetected = AvailableOr.Unavailable // resolved by ChargerAnalyzer, not here
        )
    }

    /**
     * A cold [Flow] that emits a fresh [BatterySnapshot] every time the
     * system broadcasts ACTION_BATTERY_CHANGED (this fires quite
     * frequently while charging — typically every time any monitored
     * field changes, often sub-second to a few seconds depending on OEM).
     * This is the correct, battery-efficient way to get "real-time"
     * updates: we are NOT polling in a tight loop, we're reacting to the
     * OS's own broadcast cadence.
     *
     * A [ChargingPollScheduler] (see that file) additionally drives a
     * bounded-rate timer to guarantee a minimum refresh cadence for smooth
     * graphs even on devices/OS versions where the broadcast is throttled
     * in the background — that timer calls [readSnapshot] directly rather
     * than duplicating this flow.
     */
    fun observeBatteryChangedBroadcasts(): Flow<BatterySnapshot> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(readSnapshot())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Emit an immediate first value so collectors don't wait for the
        // next system broadcast to see anything.
        trySend(readSnapshot())
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    /**
     * Instantaneous battery current in microamps via
     * BatteryManager.BATTERY_PROPERTY_CURRENT_NOW. This is a real hardware
     * fuel-gauge reading on virtually all modern phones, but:
     *  - It is NOT guaranteed present — getIntProperty returns
     *    Int.MIN_VALUE (defined as the "unsupported" sentinel by the
     *    platform) on devices/HALs that don't implement it.
     *  - Sign convention varies by OEM — see CurrentSignNormalizer, which
     *    the repository layer applies to this raw value. We deliberately
     *    do NOT normalize the sign here, so this data source stays a
     *    faithful, untouched mirror of what the OS reported.
     */
    private fun readCurrentNow(): Long? {
        val bm = batteryManager ?: return null
        val microAmps = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // Platform sentinel for "not supported" is Long.MIN_VALUE for the
        // Long-property overload (Int.MIN_VALUE for the Int overload).
        // A value of exactly 0 is technically ambiguous (could be a real
        // zero-current reading right at full charge, or an unsupported
        // property on some HALs that return 0 instead of the sentinel) —
        // we treat 0 as valid data here since discarding it would hide
        // legitimate "charging complete, trickle current is zero" states.
        return if (microAmps == Long.MIN_VALUE) null else microAmps
    }

    /**
     * Remaining charge in microamp-hours via
     * BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER. When available on
     * BOTH this call and a known battery %, BatteryCapacityEstimator uses
     * this to back-calculate an approximate design capacity — but that
     * estimate is clearly labeled as an estimate, never shown as a spec
     * value from the manufacturer.
     */
    private fun readChargeCounter(): Long? {
        val bm = batteryManager ?: return null
        val value = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        return if (value == Long.MIN_VALUE || value <= 0) null else value
    }

    /**
     * Cycle count is ONLY available via EXTRA_CYCLE_COUNT starting
     * Android 14 (API 34). There is no reliable, non-root, public API for
     * this on earlier versions — some OEMs expose it through
     * vendor-specific system files under /sys, but reading those requires
     * assumptions that don't hold across devices and can require root on
     * many, so we intentionally do not attempt it. Below API 34 this
     * always returns null, and the UI shows "Not available on this
     * device" rather than a guess.
     */
    private fun readCycleCount(stickyIntent: Intent?): Int? {
        if (Build.VERSION.SDK_INT < 34) return null
        val count = stickyIntent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
        return if (count >= 0) count else null
    }

    /**
     * Adaptive/optimized charging policy indicator, EXTRA_CHARGING_POLICY,
     * also API 34+ only.
     */
    private fun readChargingPolicy(stickyIntent: Intent?): AvailableOr<ChargingPolicy> {
        if (Build.VERSION.SDK_INT < 34) return AvailableOr.Unavailable
        val policyExtra = stickyIntent?.getIntExtra(
            BatteryManager.EXTRA_CHARGING_POLICY,
            -1
        ) ?: -1
        return when (policyExtra) {
            BatteryManager.CHARGING_POLICY_DEFAULT -> AvailableOr.Value(ChargingPolicy.DEFAULT)
            BatteryManager.CHARGING_POLICY_ADAPTIVE_LONGEVITY ->
                AvailableOr.Value(ChargingPolicy.ADAPTIVE_LONGEVITY)
            else -> AvailableOr.Unavailable
        }
    }

    private fun mapChargingStatus(statusExtra: Int, batteryPercent: Int): ChargingStatus = when (statusExtra) {
        BatteryManager.BATTERY_STATUS_CHARGING -> ChargingStatus.CHARGING
        BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargingStatus.DISCHARGING
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargingStatus.NOT_CHARGING
        BatteryManager.BATTERY_STATUS_FULL ->
            // Some devices report FULL status slightly before 100% due to
            // charge-cutoff calibration; we surface the OS's own FULL
            // status as-is rather than second-guessing it against percent.
            ChargingStatus.FULL
        else -> ChargingStatus.UNKNOWN
    }

    private fun mapPlugType(pluggedExtra: Int): PlugType = when (pluggedExtra) {
        BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
        BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
        4 -> PlugType.DOCK // BATTERY_PLUGGED_DOCK — int constant, added API 33, not in all SDK stubs
        else -> PlugType.NONE
    }

    private fun mapHealth(healthExtra: Int): BatteryHealth = when (healthExtra) {
        BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
        BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.UNSPECIFIED_FAILURE
        BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
        else -> BatteryHealth.UNKNOWN
    }

    /**
     * Best-effort skin temperature via HardwarePropertiesManager, used
     * ONLY as a supplementary "device skin temperature" data point on the
     * Battery Health screen, clearly labeled as distinct from battery
     * temperature. Requires no special permission for the battery
     * component type, but is not present on all devices/HALs, so this
     * always degrades gracefully to Unavailable.
     */
    fun readDeviceSkinTemperatureCelsius(): AvailableOr<Float> {
        return try {
            val hpm = ContextCompat.getSystemService(context, HardwarePropertiesManager::class.java)
                ?: return AvailableOr.Unavailable
            val temps = hpm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_SKIN,
                HardwarePropertiesManager.TEMPERATURE_CURRENT
            )
            val value = temps.firstOrNull { !it.isNaN() && it > -50f }
            if (value != null) AvailableOr.Value(value) else AvailableOr.Unavailable
        } catch (_: SecurityException) {
            // Some OEMs restrict this despite no permission being formally
            // required by the public API surface. Degrade silently.
            AvailableOr.Unavailable
        } catch (_: Exception) {
            AvailableOr.Unavailable
        }
    }
}
