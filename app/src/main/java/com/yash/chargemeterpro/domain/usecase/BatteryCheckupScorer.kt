package com.yash.chargemeterpro.domain.usecase

import com.yash.chargemeterpro.data.checkup.DeviceDiagnostics
import com.yash.chargemeterpro.data.usage.AppUsageInfo

/**
 * Produces the Battery Checkup result: a 0-100 score plus a list of
 * concrete, actionable findings, built entirely from real signals —
 * [DeviceDiagnostics] (live PowerManager/Settings.System reads) and
 * today's [AppUsageInfo] list (real UsageStatsManager data, same source
 * as the Usage tab). Nothing here is fabricated: every deduction traces
 * back to a specific real reading, and every finding names the exact
 * setting or app responsible so the user can act on it.
 *
 * The 100-point starting budget is split across the checks we can
 * actually perform, deducting only for a check that both (a) succeeded
 * in reading real data, and (b) found something suboptimal — a failed
 * read (null) never deducts points, since we're not confident enough in
 * missing data to penalize the user for it.
 */
object BatteryCheckupScorer {

    enum class Severity { GOOD, INFO, WARNING }

    data class Finding(
        val title: String,
        val detail: String,
        val severity: Severity,
        /** Human-readable suggested fix, shown as an action line under the finding. Null when there's nothing to do (e.g. already optimal). */
        val suggestion: String? = null
    )

    data class CheckupResult(
        val score: Int, // 0-100
        val findings: List<Finding>,
        val topDrainingApps: List<AppUsageInfo> // top 3, for the "which apps are eating battery" section
    )

    private const val TOP_APP_COUNT = 3
    private const val HEAVY_USAGE_FRACTION_THRESHOLD = 0.30f // one app using 30%+ of the day's tracked foreground time

    fun score(diagnostics: DeviceDiagnostics, todaysApps: List<AppUsageInfo>): CheckupResult {
        var points = 100.0
        val findings = mutableListOf<Finding>()

        // --- Battery Saver ---------------------------------------------
        when (diagnostics.isPowerSaveMode) {
            true -> {
                findings.add(
                    Finding(
                        title = "Battery Saver is on",
                        detail = "Your device is already reducing background activity and performance to save power.",
                        severity = Severity.GOOD
                    )
                )
            }
            false -> {
                points -= 15.0
                findings.add(
                    Finding(
                        title = "Battery Saver is off",
                        detail = "Turning it on reduces background activity and can meaningfully extend how long your charge lasts.",
                        severity = Severity.WARNING,
                        suggestion = "Turn on Battery Saver from your quick settings or Settings > Battery."
                    )
                )
            }
            null -> Unit // couldn't be read — no deduction, no claim either way
        }

        // --- Screen brightness -------------------------------------------
        diagnostics.screenBrightnessFraction?.let { fraction ->
            when {
                fraction >= 0.75f -> {
                    points -= 12.0
                    findings.add(
                        Finding(
                            title = "Screen brightness is very high",
                            detail = "Brightness is at ${(fraction * 100).toInt()}%. The display is typically one of the biggest single drains on battery life.",
                            severity = Severity.WARNING,
                            suggestion = "Lower brightness, or turn on adaptive brightness so it adjusts automatically."
                        )
                    )
                }
                fraction >= 0.50f -> {
                    points -= 5.0
                    findings.add(
                        Finding(
                            title = "Screen brightness is moderately high",
                            detail = "Brightness is at ${(fraction * 100).toInt()}%. Lowering it further would save some extra battery.",
                            severity = Severity.INFO,
                            suggestion = "Consider lowering brightness a bit more, especially indoors."
                        )
                    )
                }
                else -> {
                    findings.add(
                        Finding(
                            title = "Screen brightness looks efficient",
                            detail = "Brightness is at ${(fraction * 100).toInt()}%, which is a battery-friendly level.",
                            severity = Severity.GOOD
                        )
                    )
                }
            }
        }

        if (diagnostics.isAdaptiveBrightnessOn == false) {
            points -= 3.0
            findings.add(
                Finding(
                    title = "Adaptive brightness is off",
                    detail = "Adaptive brightness automatically keeps the screen from running brighter than it needs to.",
                    severity = Severity.INFO,
                    suggestion = "Turn on adaptive/auto brightness in Display settings."
                )
            )
        }

        // --- Screen timeout ------------------------------------------------
        diagnostics.screenTimeoutMillis?.let { timeoutMs ->
            val timeoutSeconds = timeoutMs / 1000
            when {
                timeoutSeconds > 120 -> {
                    points -= 8.0
                    findings.add(
                        Finding(
                            title = "Screen timeout is long",
                            detail = "Your screen stays on for ${formatTimeout(timeoutMs)} before locking, keeping the display running longer than it needs to between uses.",
                            severity = Severity.WARNING,
                            suggestion = "Shorten screen timeout to 30 seconds or 1 minute in Display settings."
                        )
                    )
                }
                timeoutSeconds > 60 -> {
                    points -= 3.0
                    findings.add(
                        Finding(
                            title = "Screen timeout could be shorter",
                            detail = "Your screen stays on for ${formatTimeout(timeoutMs)} before locking.",
                            severity = Severity.INFO,
                            suggestion = "A shorter timeout (15-30 seconds) saves a little extra battery over the day."
                        )
                    )
                }
                else -> {
                    findings.add(
                        Finding(
                            title = "Screen timeout is efficient",
                            detail = "Your screen locks after ${formatTimeout(timeoutMs)}, which is a battery-friendly setting.",
                            severity = Severity.GOOD
                        )
                    )
                }
            }
        }

        // --- Heavy-usage apps today -----------------------------------------
        val sortedApps = todaysApps.sortedByDescending { it.foregroundTimeMillis }
        val topApps = sortedApps.take(TOP_APP_COUNT)
        val heaviestApp = sortedApps.firstOrNull()

        if (heaviestApp != null && heaviestApp.usageFraction >= HEAVY_USAGE_FRACTION_THRESHOLD) {
            points -= 10.0
            val pct = (heaviestApp.usageFraction * 100).toInt()
            findings.add(
                Finding(
                    title = "${heaviestApp.appName} is using a large share of your screen time",
                    detail = "${heaviestApp.appName} accounts for about $pct% of today's tracked screen time" +
                        (heaviestApp.batteryPercent?.let { " and an estimated ${it.toInt()}% of today's battery use" } ?: "") + ".",
                    severity = Severity.WARNING,
                    suggestion = "If this doesn't match how you actually use it, check its background/notification settings."
                )
            )
        } else if (topApps.isNotEmpty()) {
            findings.add(
                Finding(
                    title = "No single app is dominating battery use",
                    detail = "Today's usage looks reasonably spread across your apps rather than concentrated in one.",
                    severity = Severity.GOOD
                )
            )
        }

        val clamped = points.coerceIn(0.0, 100.0).toInt()
        return CheckupResult(
            score = clamped,
            findings = findings,
            topDrainingApps = topApps
        )
    }

    private fun formatTimeout(millis: Int): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return when {
            minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes} min"
            else -> "${seconds}s"
        }
    }
}
