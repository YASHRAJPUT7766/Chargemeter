package com.yash.chargemeterpro.di

import android.content.Context
import androidx.room.Room
import com.yash.chargemeterpro.data.local.ChargeMeterDatabase
import com.yash.chargemeterpro.data.local.dao.ChargingSampleDao
import com.yash.chargemeterpro.data.local.dao.ChargingSessionDao
import com.yash.chargemeterpro.data.local.dao.DrainSampleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ChargeMeterDatabase =
        Room.databaseBuilder(
            context,
            ChargeMeterDatabase::class.java,
            ChargeMeterDatabase.DATABASE_NAME
        )
            // No destructive fallback in production — schema changes must
            // ship a real Migration so a user's charging history is never
            // silently wiped by an app update.
            .build()

    @Provides
    fun provideChargingSessionDao(db: ChargeMeterDatabase): ChargingSessionDao = db.chargingSessionDao()

    @Provides
    fun provideChargingSampleDao(db: ChargeMeterDatabase): ChargingSampleDao = db.chargingSampleDao()

    @Provides
    fun provideDrainSampleDao(db: ChargeMeterDatabase): DrainSampleDao = db.drainSampleDao()
}
