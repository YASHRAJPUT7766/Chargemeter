package com.yash.chargemeterpro.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One time-series data point within a charging session — this is what
 * backs the "Wattage vs Time", "Current vs Time", etc. graphs, and the
 * Charging Speed Test's per-interval log.
 *
 * Sampling cadence is controlled by ChargingPollScheduler (typically
 * every 2-5 seconds while the app is foregrounded and actively charging,
 * widened while backgrounded to conserve battery — see that file for the
 * exact cadence policy). We deliberately do NOT store every single
 * ACTION_BATTERY_CHANGED broadcast verbatim, since on some devices that
 * can fire quite often and would bloat the local DB for no analytical
 * benefit beyond what a several-second cadence already captures.
 */
@Entity(
    tableName = "charging_samples",
    foreignKeys = [
        ForeignKey(
            entity = ChargingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("timestampMillis")]
)
data class ChargingSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: Long,
    val timestampMillis: Long,

    val batteryPercent: Int,
    val voltageVolts: Double?,
    val currentMilliAmps: Double?, // sign-normalized: positive = into battery
    val powerWatts: Double?,
    val temperatureCelsius: Double?
)
