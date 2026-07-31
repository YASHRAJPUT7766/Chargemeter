package com.yash.chargemeterpro.data.repository

import com.yash.chargemeterpro.data.local.dao.ChargingSampleDao
import com.yash.chargemeterpro.data.local.dao.ChargingSessionDao
import com.yash.chargemeterpro.data.local.entity.ChargingSampleEntity
import com.yash.chargemeterpro.data.local.entity.ChargingSessionEntity
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.usecase.PowerCalculator
import com.yash.chargemeterpro.domain.usecase.SessionEnergyIntegrator
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the full lifecycle of a charging session:
 *  startSession() -> recordSample() [repeated] -> endSession()
 *
 * Called from ChargingMonitorService (the foreground service backing
 * "Always On Charging Monitor") when enabled, and from
 * LiveMonitorViewModel's own collection loop when the app is in the
 * foreground and the service isn't running — either caller path produces
 * identical DB writes, so History/Statistics behave the same regardless
 * of which one was actually recording.
 */
@Singleton
class ChargingSessionRepository @Inject constructor(
    private val sessionDao: ChargingSessionDao,
    private val sampleDao: ChargingSampleDao
) {
    fun observeActiveSession(): Flow<ChargingSessionEntity?> = sessionDao.observeActiveSession()

    fun observeAllSessions(): Flow<List<ChargingSessionEntity>> = sessionDao.observeAll()

    fun observeSamplesForSession(sessionId: Long): Flow<List<ChargingSampleEntity>> =
        sampleDao.observeForSession(sessionId)

    suspend fun getActiveSession(): ChargingSessionEntity? = sessionDao.getActiveSession()

    suspend fun getSessionById(sessionId: Long): ChargingSessionEntity? = sessionDao.getById(sessionId)

    /**
     * Starts a new session. Guards against double-starting: if a
     * still-active session already exists (e.g. the service restarted
     * mid-charge after a process death), we resume it rather than
     * creating a duplicate — the resumed session's startBatteryPercent is
     * left untouched from its original value.
     */
    suspend fun startSession(snapshot: BatterySnapshot): Long {
        val existing = sessionDao.getActiveSession()
        if (existing != null) return existing.id

        val entity = ChargingSessionEntity(
            startTimeMillis = snapshot.timestampMillis,
            endTimeMillis = null,
            startBatteryPercent = snapshot.batteryPercent,
            endBatteryPercent = null,
            plugTypeName = snapshot.plugType.name,
            averageCurrentMilliAmps = null,
            averagePowerWatts = null,
            maxPowerWatts = null,
            maxCurrentMilliAmps = null,
            minTemperatureCelsius = null,
            maxTemperatureCelsius = null,
            estimatedEnergyWattHours = null,
            wasCompletedNormally = false
        )
        return sessionDao.insert(entity)
    }

    /** Records one time-series point for the given active session. Cheap, safe to call every poll tick. */
    suspend fun recordSample(sessionId: Long, snapshot: BatterySnapshot) {
        val powerWatts = PowerCalculator.batteryInputPowerWatts(snapshot)
        val sample = ChargingSampleEntity(
            sessionId = sessionId,
            timestampMillis = snapshot.timestampMillis,
            batteryPercent = snapshot.batteryPercent,
            voltageVolts = snapshot.voltageVolts,
            currentMilliAmps = snapshot.currentMilliAmpsNormalized,
            powerWatts = powerWatts,
            temperatureCelsius = snapshot.temperatureC
        )
        sampleDao.insert(sample)
    }

    /**
     * Closes out the session: computes every aggregate stat from the
     * full sample series via [SessionEnergyIntegrator] and persists them
     * onto the session row, so History/Statistics never need to
     * re-aggregate raw samples on every screen open.
     */
    suspend fun endSession(sessionId: Long, finalSnapshot: BatterySnapshot, completedNormally: Boolean = true) {
        val session = sessionDao.getById(sessionId) ?: return
        val samples = sampleDao.getForSession(sessionId)

        val updated = session.copy(
            endTimeMillis = finalSnapshot.timestampMillis,
            endBatteryPercent = finalSnapshot.batteryPercent,
            averageCurrentMilliAmps = SessionEnergyIntegrator.averageCurrentMilliAmps(samples),
            averagePowerWatts = SessionEnergyIntegrator.averagePowerWatts(samples),
            maxPowerWatts = SessionEnergyIntegrator.maxPowerWatts(samples),
            maxCurrentMilliAmps = SessionEnergyIntegrator.maxCurrentMilliAmps(samples),
            minTemperatureCelsius = SessionEnergyIntegrator.minTemperature(samples),
            maxTemperatureCelsius = SessionEnergyIntegrator.maxTemperature(samples),
            estimatedEnergyWattHours = SessionEnergyIntegrator.integrateWattHours(samples),
            wasCompletedNormally = completedNormally
        )
        sessionDao.update(updated)
    }

    suspend fun deleteSession(sessionId: Long) = sessionDao.deleteById(sessionId)

    suspend fun deleteAllHistory() = sessionDao.deleteAll()

    fun observeTopSessionsByMaxPower(limit: Int = 20) = sessionDao.observeTopSessionsByMaxPower(limit)

    fun observeSessionsForDay(startOfDayMillis: Long, endOfDayMillis: Long) =
        sessionDao.observeSessionsForDay(startOfDayMillis, endOfDayMillis)

    fun observeOverallAveragePowerWatts() = sessionDao.observeOverallAveragePowerWatts()

    fun observeTotalEnergyWattHours() = sessionDao.observeTotalEnergyWattHours()

    fun observeCompletedSessionCount() = sessionDao.observeCompletedSessionCount()
}
