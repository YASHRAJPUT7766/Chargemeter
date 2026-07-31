package com.yash.chargemeterpro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yash.chargemeterpro.data.local.entity.DrainSampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DrainSampleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: DrainSampleEntity): Long

    @Query("SELECT * FROM drain_samples WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
    fun observeSince(sinceMillis: Long): Flow<List<DrainSampleEntity>>

    @Query("SELECT * FROM drain_samples WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
    suspend fun getSince(sinceMillis: Long): List<DrainSampleEntity>

    @Query("SELECT * FROM drain_samples ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getMostRecent(): DrainSampleEntity?

    @Query("DELETE FROM drain_samples WHERE timestampMillis < :olderThanMillis")
    suspend fun pruneOlderThan(olderThanMillis: Long)

    @Query("DELETE FROM drain_samples")
    suspend fun deleteAll()
}
