package com.yash.chargemeterpro.domain.usecase

import android.content.Context
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.yash.chargemeterpro.domain.model.AvailableOr
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.model.PlugType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything this app is willing to say about the CHARGER itself, as
 * opposed to the battery. This is deliberately the most conservative
 * piece of logic in the codebase, because charger/protocol claims are
 * the easiest place for a battery app to lie to users (either by
 * fabricating "Quick Charge 5.0 detected!" claims with no real signal
 * behind them, or by implying wall-side wattage was measured when it
 * wasn't).
 *
 * What Android genuinely, reliably exposes about the charger connection:
 *  - Plug type: AC / USB / Wireless / Dock / None (EXTRA_PLUGGED) — reliable.
 *  - Whether a USB accessory/device is attached and its basic descriptor
 *    info, via UsbManager — reliable for "a USB connection exists", NOT
 *    reliable for "this is a USB-PD charger" (dumb/passive USB-A power
 *    bricks enumerate no USB device descriptor at all, since there's no
 *    data-line negotiation happening, so absence of a descriptor tells us
 *    nothing about wattage).
 *  - EXTRA_MAX_CHARGING_CURRENT / EXTRA_MAX_CHARGING_VOLTAGE — the
 *    platform's OWN estimate of negotiated max input, when the OEM's
 *    charging HAL populates it. Best available signal, but explicitly
 *    optional per the platform and absent on many devices/ROMs.
 *
 * What Android does NOT reliably expose, and what we refuse to guess:
 *  - The specific USB-PD Power Delivery contract that was negotiated
 *    (e.g. "9V/2A PPS profile") — this negotiation happens in hardware
 *    below any public Android API.
 *  - Whether a specific proprietary fast-charge protocol (Quick Charge,
 *    proprietary OEM schemes, etc.) is active — same reason.
 *  - The charger's actual rated wattage / wall-side power draw.
 */
@Singleton
class ChargerAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usbManager: UsbManager? =
        ContextCompat.getSystemService(context, UsbManager::class.java)

    fun analyze(snapshot: BatterySnapshot): ChargerAnalysis {
        val usbConnected = snapshot.plugType == PlugType.USB || snapshot.plugType == PlugType.AC
        val usbDeviceAttached = try {
            usbManager?.deviceList?.isNotEmpty() == true
        } catch (_: Exception) {
            false
        }

        val hasDeviceReportedMax = snapshot.maxChargingCurrentMicroAmps.isAvailable

        // We only ever say "fast charging indicated" when we have an
        // actual measured wattage that clears a fast-charge floor AND
        // (where available) it's corroborated by the device's own
        // reported max. We never claim a *named* protocol.
        val estimatedWatts = PowerCalculator.batteryInputPowerWatts(snapshot)
        val fastChargeIndicated = estimatedWatts != null && estimatedWatts >= 15.0

        val protocolDetail: AvailableOr<String> = when {
            hasDeviceReportedMax -> {
                val maxUa = snapshot.maxChargingCurrentMicroAmps.orNull()
                val maxUv = snapshot.maxChargingVoltageMicroVolts.orNull()
                if (maxUa != null) {
                    val maxA = maxUa / 1_000_000.0
                    val maxV = maxUv?.let { it / 1_000_000.0 }
                    val desc = if (maxV != null) {
                        "Device reports max charging input: %.2fV / %.2fA".format(maxV, maxA)
                    } else {
                        "Device reports max charging current: %.2fA".format(maxA)
                    }
                    AvailableOr.Value(desc)
                } else AvailableOr.Unavailable
            }
            else -> AvailableOr.Unavailable
        }

        return ChargerAnalysis(
            plugType = snapshot.plugType,
            usbConnected = usbConnected,
            usbDeviceDescriptorPresent = usbDeviceAttached,
            fastChargeIndicated = fastChargeIndicated,
            deviceReportedMaxInputDetail = protocolDetail,
            usbPdStatus = AvailableOr.Unavailable, // see class doc — no reliable public signal exists
            wallOutputMeasurable = false // always false — see PowerTerminology.WALL_OUTPUT_UNAVAILABLE_DISCLAIMER
        )
    }
}

data class ChargerAnalysis(
    val plugType: PlugType,
    val usbConnected: Boolean,
    /**
     * True if a USB device descriptor is enumerated. NOTE: this is a weak
     * signal — many passive USB-A power bricks enumerate NOTHING because
     * no data-line negotiation occurs, so false here does not mean "not
     * charging" or "low quality charger", only "no USB data device was
     * enumerated". The UI must not imply otherwise.
     */
    val usbDeviceDescriptorPresent: Boolean,
    /** Derived purely from measured wattage clearing a fast-charge floor — see ChargingSpeedClassifier. */
    val fastChargeIndicated: Boolean,
    val deviceReportedMaxInputDetail: AvailableOr<String>,
    /**
     * Always Unavailable — no public Android API reliably exposes USB-PD
     * contract details. Kept explicit rather than omitted so the UI has
     * one clear field to render "Not available on this device" for.
     */
    val usbPdStatus: AvailableOr<String>,
    /**
     * Always false. Present as a named field so calling code/UI can't
     * accidentally treat wall output as measurable.
     */
    val wallOutputMeasurable: Boolean
)
