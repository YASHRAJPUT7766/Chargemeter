package com.yash.chargemeterpro.domain.usecase

import com.yash.chargemeterpro.data.local.entity.ChargingSampleEntity

/**
 * Turns a series of discrete power samples (Watts at a point in time)
 * into a cumulative energy estimate (Watt-hours) using trapezoidal
 * integration — averaging each consecutive pair of samples and
 * multiplying by the elapsed time between them, then summing.
 *
 * This is more accurate than the naive "average power x total duration"
 * shortcut when charging power isn't constant (which it never really is —
 * it tapers as the battery fills, and dips/spikes with thermal
 * throttling), because it actually accounts for the SHAPE of the power
 * curve over the session rather than a single flat average.
 *
 * Still explicitly an ESTIMATE of energy delivered to the BATTERY, not
 * drawn from the wall — see PowerTerminology.EFFICIENCY_DISCLAIMER,
 * shown wherever this number is displayed.
 */
object SessionEnergyIntegrator {

    fun integrateWattHours(samples: List<ChargingSampleEntity>): Double? {
        val usable = samples.filter { it.powerWatts != null }
        if (usable.size < 2) return null

        var totalWattHours = 0.0
        for (i in 0 until usable.size - 1) {
            val a = usable[i]
            val b = usable[i + 1]
            val powerA = a.powerWatts ?: continue
            val powerB = b.powerWatts ?: continue
            val elapsedHours = (b.timestampMillis - a.timestampMillis) / 3_600_000.0
            if (elapsedHours <= 0.0) continue
            val avgPower = (powerA + powerB) / 2.0
            totalWattHours += avgPower * elapsedHours
        }
        return totalWattHours
    }

    fun averagePowerWatts(samples: List<ChargingSampleEntity>): Double? {
        val values = samples.mapNotNull { it.powerWatts }
        if (values.isEmpty()) return null
        return values.average()
    }

    fun maxPowerWatts(samples: List<ChargingSampleEntity>): Double? =
        samples.mapNotNull { it.powerWatts }.maxOrNull()

    fun maxCurrentMilliAmps(samples: List<ChargingSampleEntity>): Double? =
        samples.mapNotNull { it.currentMilliAmps }.maxOrNull()

    fun averageCurrentMilliAmps(samples: List<ChargingSampleEntity>): Double? {
        val values = samples.mapNotNull { it.currentMilliAmps }
        if (values.isEmpty()) return null
        return values.average()
    }

    fun minTemperature(samples: List<ChargingSampleEntity>): Double? =
        samples.mapNotNull { it.temperatureCelsius }.minOrNull()

    fun maxTemperature(samples: List<ChargingSampleEntity>): Double? =
        samples.mapNotNull { it.temperatureCelsius }.maxOrNull()
}
