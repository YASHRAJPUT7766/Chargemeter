package com.yash.chargemeterpro.data.battery

/**
 * Central definition of how often we sample battery state, in each
 * app-lifecycle context. Kept as one object so cadence tuning happens in
 * exactly one place rather than as magic numbers scattered across
 * ViewModels/services.
 *
 * Rationale for each value:
 *  - FOREGROUND_ACTIVE: the Live Monitor / Home screen graphs need to
 *    feel real-time. ACTION_BATTERY_CHANGED broadcasts already arrive
 *    fairly often while charging, but aren't guaranteed at a fixed
 *    interval — this interval is a supplementary timer tick so graphs
 *    don't visibly stall between broadcasts, layered on top of (not
 *    replacing) the broadcast-driven flow in BatteryDataSource.
 *  - BACKGROUND_SERVICE: the "Always On Charging Monitor" foreground
 *    service samples less aggressively than the foreground UI, since
 *    nothing is being visually animated — this is purely for session
 *    history accuracy and alert timeliness, so a coarser interval is
 *    both sufficient and meaningfully kinder to battery life, which
 *    matters a lot for an app whose entire purpose is monitoring
 *    battery health.
 *  - DRAIN_MONITOR: sampled via periodic WorkManager (minimum ~15 min
 *    enforced by the platform for periodic work), since drain trends
 *    don't need sub-minute granularity the way active charging does.
 */
object ChargingPollScheduler {
    const val FOREGROUND_ACTIVE_INTERVAL_MS = 2_000L
    const val BACKGROUND_SERVICE_INTERVAL_MS = 15_000L
    const val SPEED_TEST_INTERVAL_MS = 1_000L // Speed Test wants tighter resolution for its short, bounded duration
    const val DRAIN_MONITOR_WORK_INTERVAL_MINUTES = 15L // platform-enforced minimum for PeriodicWorkRequest
}
