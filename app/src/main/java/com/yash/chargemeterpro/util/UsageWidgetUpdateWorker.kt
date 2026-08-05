package com.yash.chargemeterpro.util

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yash.chargemeterpro.data.usage.UsageStatsRepository
import com.yash.chargemeterpro.widget.UsageWidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * Refreshes UsageWidget with today's screen-time-so-far. Runs on the same
 * kind of periodic WorkManager schedule as DrainMonitorWorker, but at a
 * longer interval — usage totals only meaningfully change as the user
 * actively uses the phone, and a UsageStatsManager query is heavier than
 * a battery snapshot read, so there's no benefit to polling as often as
 * the charging-focused workers do.
 *
 * No-ops (leaves the widget on its last good state) if Usage Access
 * isn't granted rather than pushing a "no access" state on every single
 * run — [UsageWidgetUpdater.pushNoAccess] is only called once meaningfully
 * different: see doWork() below, it's still called each run when access
 * is missing, since that's the accurate current state and cheap to write.
 */
@HiltWorker
class UsageWidgetUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val usageStatsRepository: UsageStatsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            if (!usageStatsRepository.hasUsageAccess()) {
                UsageWidgetUpdater.pushNoAccess(applicationContext)
                return Result.success()
            }

            val today = LocalDate.now().toEpochDay()
            val summary = usageStatsRepository.getDailySummary(today)
            val topApp = summary.apps.maxByOrNull { it.foregroundTimeMillis }

            UsageWidgetUpdater.pushSummary(
                context = applicationContext,
                totalForegroundMillis = summary.totalForegroundTimeMillis,
                topAppName = topApp?.appName,
                unlockCount = summary.unlockCount
            )

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
