package com.yash.chargemeterpro.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yash.chargemeterpro.data.local.dao.ChargingSampleDao
import com.yash.chargemeterpro.data.local.dao.ChargingSessionDao
import com.yash.chargemeterpro.data.local.dao.DrainSampleDao
import com.yash.chargemeterpro.data.local.entity.ChargingSampleEntity
import com.yash.chargemeterpro.data.local.entity.ChargingSessionEntity
import com.yash.chargemeterpro.data.local.entity.DrainSampleEntity

/**
 * All charging history lives in this single local, on-device SQLite
 * database via Room. Nothing in this module makes a network call — see
 * DATA_PRIVACY section of README.md and PrivacyPolicyScreen.kt for the
 * user-facing statement of this same guarantee. Optional cloud backup
 * (if the user explicitly enables it in Settings) is handled entirely
 * outside this class by a separate, clearly-opt-in sync module — this
 * database has no awareness of or dependency on that feature.
 */
@Database(
    entities = [
        ChargingSessionEntity::class,
        ChargingSampleEntity::class,
        DrainSampleEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ChargeMeterDatabase : RoomDatabase() {
    abstract fun chargingSessionDao(): ChargingSessionDao
    abstract fun chargingSampleDao(): ChargingSampleDao
    abstract fun drainSampleDao(): DrainSampleDao

    companion object {
        const val DATABASE_NAME = "chargemeter_pro.db"
    }
}
