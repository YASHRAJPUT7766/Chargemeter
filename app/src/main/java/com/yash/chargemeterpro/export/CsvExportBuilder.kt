package com.yash.chargemeterpro.export

import android.content.Context
import androidx.core.content.FileProvider
import com.yash.chargemeterpro.BuildConfig
import com.yash.chargemeterpro.data.local.entity.ChargingSampleEntity
import com.yash.chargemeterpro.data.local.entity.ChargingSessionEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds CSV files for charging history export. Output files are written
 * to the app's own cache/exports directory and shared only through
 * FileProvider (see file_paths.xml — only that subfolder is exposed),
 * matching the extra-features requirement "Export charging history as
 * CSV" while keeping data local until the user explicitly shares it.
 *
 * Two export shapes are supported:
 *  1. [buildSessionsSummaryCsv] — one row per session, for a spreadsheet
 *     overview of charging history.
 *  2. [buildSessionSamplesCsv] — one row per raw sample within a single
 *     session, for people who want the full time-series data (e.g. to
 *     re-plot in their own tool).
 *
 * All numeric fields that could be device-unavailable are written as
 * empty CSV cells rather than "0" or "N/A" text, so the CSV remains
 * cleanly machine-parseable (an empty cell is the conventional CSV
 * representation of "no value") while still never fabricating a number.
 */
object CsvExportBuilder {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    fun buildSessionsSummaryCsv(context: Context, sessions: List<ChargingSessionEntity>): File {
        val sb = StringBuilder()
        sb.appendLine(
            listOf(
                "start_time", "end_time", "start_battery_percent", "end_battery_percent",
                "plug_type", "duration_minutes", "average_current_ma", "average_power_w",
                "max_power_w", "max_current_ma", "min_temperature_c", "max_temperature_c",
                "estimated_energy_wh"
            ).joinToString(",")
        )

        sessions.forEach { s ->
            val durationMinutes = s.endTimeMillis?.let { (it - s.startTimeMillis) / 60000 }
            sb.appendLine(
                listOf(
                    isoFormat.format(Date(s.startTimeMillis)),
                    s.endTimeMillis?.let { isoFormat.format(Date(it)) } ?: "",
                    s.startBatteryPercent.toString(),
                    s.endBatteryPercent?.toString() ?: "",
                    s.plugTypeName,
                    durationMinutes?.toString() ?: "",
                    s.averageCurrentMilliAmps?.let { "%.1f".format(it) } ?: "",
                    s.averagePowerWatts?.let { "%.3f".format(it) } ?: "",
                    s.maxPowerWatts?.let { "%.3f".format(it) } ?: "",
                    s.maxCurrentMilliAmps?.let { "%.1f".format(it) } ?: "",
                    s.minTemperatureCelsius?.let { "%.1f".format(it) } ?: "",
                    s.maxTemperatureCelsius?.let { "%.1f".format(it) } ?: "",
                    s.estimatedEnergyWattHours?.let { "%.3f".format(it) } ?: ""
                ).joinToString(",") { escapeCsvField(it) }
            )
        }

        return writeToExportsFile(context, "chargemeter_history_${fileTimestamp()}.csv", sb.toString())
    }

    fun buildSessionSamplesCsv(context: Context, sessionId: Long, samples: List<ChargingSampleEntity>): File {
        val sb = StringBuilder()
        sb.appendLine(listOf("timestamp", "battery_percent", "voltage_v", "current_ma", "power_w", "temperature_c").joinToString(","))

        samples.forEach { s ->
            sb.appendLine(
                listOf(
                    isoFormat.format(Date(s.timestampMillis)),
                    s.batteryPercent.toString(),
                    s.voltageVolts?.let { "%.3f".format(it) } ?: "",
                    s.currentMilliAmps?.let { "%.1f".format(it) } ?: "",
                    s.powerWatts?.let { "%.3f".format(it) } ?: "",
                    s.temperatureCelsius?.let { "%.1f".format(it) } ?: ""
                ).joinToString(",") { escapeCsvField(it) }
            )
        }

        return writeToExportsFile(context, "chargemeter_session_${sessionId}_${fileTimestamp()}.csv", sb.toString())
    }

    /** Wraps a field in quotes and escapes embedded quotes only if needed — most fields here are numeric/ISO-date and never need it, but plug_type or future free-text notes might. */
    private fun escapeCsvField(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else field
    }

    private fun fileTimestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun writeToExportsFile(context: Context, filename: String, content: String): File {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, filename)
        file.writeText(content)
        return file
    }

    fun shareUriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
}
