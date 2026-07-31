package com.yash.chargemeterpro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yash.chargemeterpro.data.local.entity.ChargingSampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingSampleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: ChargingSampleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<ChargingSampleEntity>)

    @Query("SELECT * FROM charging_samples WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    fun observeForSession(sessionId: Long): Flow<List<ChargingSampleEntity>>

    @Query("SELECT * FROM charging_samples WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    suspend fun getForSession(sessionId: Long): List<ChargingSampleEntity>

    @Query(
        """
        SELECT * FROM charging_samples
        WHERE sessionId = :sessionId
        ORDER BY timestampMillis DESC
        LIMIT :limit
        """
    )
    fun observeRecentForSession(sessionId: Long, limit: Int): Flow<List<ChargingSampleEntity>>

    @Query("SELECT COUNT(*) FROM charging_samples WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    @Query("DELETE FROM charging_samples WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    /**
     * Housekeeping: raw samples for very old sessions are pruned after
     * [olderThanMillis] to keep local DB size bounded, while the
     * aggregate stats on ChargingSessionEntity (which is what History
     * mostly shows) are retained indefinitely. Called from a periodic
     * maintenance WorkManager job — see MaintenanceWorker.kt.
     */
    @Query(
        """
        DELETE FROM charging_samples
        WHERE sessionId IN (
            SELECT id FROM charging_sessions WHERE startTimeMillis < :olderThanMillis
        )
        """
    )
    suspend fun pruneSamplesOlderThan(olderThanMillis: Long)
}
