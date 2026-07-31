package com.yash.chargemeterpro.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single charging session, from plug-in to unplug (or app-detected
 * "charging stopped"). Aggregate stats (avg/max power, energy, etc.) are
 * computed once at session-close time from the full set of
 * [ChargingSampleEntity] rows for this session and cached here, so the
 * History/Statistics screens don't need to re-aggregate thousands of raw
 * samples on every render.
 *
 * All nullable Double/Int fields correspond 1:1 to [AvailableOr.Unavailable]
 * upstream — null here means "device didn't expose this data", never
 * "zero". The repository layer is responsible for that mapping (see
 * ChargingSessionMapper.kt).
 */
@Entity(tableName = "charging_sessions")
data class ChargingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val startTimeMillis: Long,
    val endTimeMillis: Long?, // null while session is still active

    val startBatteryPercent: Int,
    val endBatteryPercent: Int?, // null while active

    val plugTypeName: String, // PlugType enum name, e.g. "USB", "WIRELESS"

    val averageCurrentMilliAmps: Double?,
    val averagePowerWatts: Double?,
    val maxPowerWatts: Double?,
    val maxCurrentMilliAmps: Double?,

    val minTemperatureCelsius: Double?,
    val maxTemperatureCelsius: Double?,

    /** Trapezoidal-integrated estimate — see SessionEnergyIntegrator.kt. */
    val estimatedEnergyWattHours: Double?,

    val wasCompletedNormally: Boolean, // false if the app process died mid-session and we recovered a partial record

    /** Free-form note the user can attach after the fact (e.g. "office charger", "car"). */
    val userNote: String? = null
)
