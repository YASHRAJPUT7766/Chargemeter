package com.yash.chargemeterpro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yash.chargemeterpro.data.local.entity.ChargingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ChargingSessionEntity): Long

    @Update
    suspend fun update(session: ChargingSessionEntity)

    @Query("SELECT * FROM charging_sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: Long): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions WHERE id = :sessionId")
    fun observeById(sessionId: Long): Flow<ChargingSessionEntity?>

    @Query("SELECT * FROM charging_sessions ORDER BY startTimeMillis DESC")
    fun observeAll(): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE endTimeMillis IS NULL ORDER BY startTimeMillis DESC LIMIT 1")
    suspend fun getActiveSession(): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions WHERE endTimeMillis IS NULL ORDER BY startTimeMillis DESC LIMIT 1")
    fun observeActiveSession(): Flow<ChargingSessionEntity?>

    @Query(
        """
        SELECT * FROM charging_sessions
        WHERE startTimeMillis >= :startOfDayMillis AND startTimeMillis < :endOfDayMillis
        ORDER BY startTimeMillis DESC
        """
    )
    fun observeSessionsForDay(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE endTimeMillis IS NOT NULL ORDER BY maxPowerWatts DESC LIMIT :limit")
    fun observeTopSessionsByMaxPower(limit: Int = 20): Flow<List<ChargingSessionEntity>>

    @Query("SELECT COUNT(*) FROM charging_sessions WHERE endTimeMillis IS NOT NULL")
    fun observeCompletedSessionCount(): Flow<Int>

    @Query("DELETE FROM charging_sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: Long)

    @Query("DELETE FROM charging_sessions")
    suspend fun deleteAll()

    @Query(
        """
        SELECT AVG(averagePowerWatts) FROM charging_sessions
        WHERE endTimeMillis IS NOT NULL AND averagePowerWatts IS NOT NULL
        """
    )
    fun observeOverallAveragePowerWatts(): Flow<Double?>

    @Query(
        """
        SELECT SUM(estimatedEnergyWattHours) FROM charging_sessions
        WHERE endTimeMillis IS NOT NULL AND estimatedEnergyWattHours IS NOT NULL
        """
    )
    fun observeTotalEnergyWattHours(): Flow<Double?>
}
