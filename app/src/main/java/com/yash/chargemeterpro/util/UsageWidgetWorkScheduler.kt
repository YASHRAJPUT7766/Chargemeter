package com.yash.chargemeterpro.util

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules [UsageWidgetUpdateWorker] on a 30-minute cadence — WorkManager's
 * PeriodicWorkRequest enforces a 15-minute platform floor, so 30 minutes
 * comfortably respects that while still keeping UsageWidget reasonably
 * fresh without spending extra battery on UsageStatsManager queries the
 * widget's own content doesn't change fast enough to need more often.
 */
@Singleton
class UsageWidgetWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val WORK_NAME = "usage_widget_update_periodic"
        private const val INTERVAL_MINUTES = 30L
    }

    fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .build()

        val request = PeriodicWorkRequestBuilder<UsageWidgetUpdateWorker>(
            INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
