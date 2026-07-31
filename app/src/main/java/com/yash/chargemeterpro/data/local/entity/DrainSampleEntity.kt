package com.yash.chargemeterpro.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A sample taken while the device is NOT charging, used to compute
 * drain-rate-per-hour on the Battery Drain Monitor screen. Populated by a
 * periodic WorkManager job (see DrainMonitorWorker) rather than a
 * continuous foreground service, since draining doesn't need sub-minute
 * resolution the way live charging graphs do, and running a persistent
 * service purely to watch drain would itself meaningfully hurt battery
 * life — the opposite of what this app is for.
 */
@Entity(tableName = "drain_samples", indices = [Index("timestampMillis")])
data class DrainSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMillis: Long,
    val batteryPercent: Int,
    val voltageVolts: Double?,
    val currentMilliAmps: Double?, // sign-normalized: negative = draining, per app convention
    val temperatureCelsius: Double?,
    val screenOn: Boolean
)
