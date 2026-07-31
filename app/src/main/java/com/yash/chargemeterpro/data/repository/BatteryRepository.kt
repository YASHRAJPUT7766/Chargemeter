package com.yash.chargemeterpro.data.repository

import com.yash.chargemeterpro.data.battery.BatteryDataSource
import com.yash.chargemeterpro.data.battery.CurrentSignNormalizer
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin orchestration layer over [BatteryDataSource]: applies sign
 * normalization consistently to every snapshot that leaves this
 * repository, so nothing downstream (ViewModels, session tracker,
 * widget, notifications) needs to re-derive it independently or risk
 * doing it differently in different places.
 */
@Singleton
class BatteryRepository @Inject constructor(
    private val dataSource: BatteryDataSource,
    @ApplicationContext private val context: Context
) {
    fun observeSnapshots(): Flow<BatterySnapshot> =
        dataSource.observeBatteryChangedBroadcasts().map { raw -> normalize(raw) }

    fun readSnapshotNow(): BatterySnapshot = normalize(dataSource.readSnapshot())

    private fun normalize(raw: BatterySnapshot): BatterySnapshot {
        val normalizedCurrent = CurrentSignNormalizer.normalize(raw.currentMicroAmps, raw.chargingStatus)
        return raw.copy(currentMicroAmps = normalizedCurrent)
    }

    fun readDeviceSkinTemperature() = dataSource.readDeviceSkinTemperatureCelsius()
}
