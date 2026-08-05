package com.yash.chargemeterpro.export

import android.content.Context
import com.yash.chargemeterpro.domain.model.AvailableOr
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.usecase.PowerTerminology
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a simple, self-contained SVG "status card" for the current live
 * battery/charging snapshot — the SVG counterpart to
 * [PdfExportBuilder.buildLiveStatusReport], reached from the same
 * top-bar Share action. SVG is plain XML text, so this needs no
 * rendering library — it's built as a formatted string, same general
 * shape as CsvExportBuilder's plain-text approach.
 *
 * Colors deliberately mirror the app's own instrument-panel palette
 * (phosphor green accent on a near-black panel) so a shared SVG still
 * looks recognizably like Battery Stats rather than a generic light-mode
 * document.
 */
object SvgExportBuilder {

    private val dateFmt = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US)
    private val fileTimestampFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun buildLiveStatusSvg(context: Context, snapshot: BatterySnapshot): File {
        val rows = buildList {
            add("Battery Level" to "${snapshot.batteryPercent}%")
            add("Charging Status" to snapshot.chargingStatus.name)
            add("Plug Type" to snapshot.plugType.name)
            add(
                "Voltage" to ((snapshot.voltageMilliVolts as? AvailableOr.Value)?.value
                    ?.let { "%.3f V".format(it / 1000.0) } ?: PowerTerminology.NOT_AVAILABLE)
            )
            add(
                "Current" to (snapshot.currentMilliAmpsNormalized?.let { "%.1f mA".format(it) }
                    ?: PowerTerminology.NOT_AVAILABLE)
            )
            add(
                "Estimated Power" to (snapshot.batteryInputPowerWatts?.let { "%.2f W".format(it) }
                    ?: PowerTerminology.NOT_AVAILABLE)
            )
            add(
                "Temperature" to ((snapshot.temperatureCelsius as? AvailableOr.Value)?.value
                    ?.let { "%.1f °C".format(it) } ?: PowerTerminology.NOT_AVAILABLE)
            )
            add("Health" to snapshot.health.name)
            add("Technology" to (snapshot.technology.orNull() ?: PowerTerminology.NOT_AVAILABLE))
        }

        val rowHeight = 34
        val headerHeight = 96
        val footerHeight = 46
        val width = 480
        val height = headerHeight + rows.size * rowHeight + footerHeight

        val svg = buildString {
            appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">""")
            appendLine("""<rect width="$width" height="$height" fill="#0A0E12"/>""")
            appendLine("""<text x="24" y="34" font-family="sans-serif" font-size="13" font-weight="bold" fill="#39FF88">Battery Stats</text>""")
            appendLine("""<text x="24" y="64" font-family="sans-serif" font-size="22" font-weight="bold" fill="#FFFFFF">Charging Status</text>""")
            appendLine(
                """<text x="24" y="86" font-family="sans-serif" font-size="12" fill="#5A6470">${escapeXml(dateFmt.format(Date(snapshot.timestampMillis)))}</text>"""
            )
            appendLine("""<line x1="24" y1="$headerHeight" x2="${width - 24}" y2="$headerHeight" stroke="#232B33" stroke-width="1"/>""")

            rows.forEachIndexed { index, (label, value) ->
                val y = headerHeight + 24 + index * rowHeight
                appendLine("""<text x="24" y="$y" font-family="sans-serif" font-size="13" fill="#5A6470">${escapeXml(label)}</text>""")
                appendLine("""<text x="${width - 24}" y="$y" font-family="sans-serif" font-size="13" font-weight="bold" fill="#FFFFFF" text-anchor="end">${escapeXml(value)}</text>""")
                if (index < rows.size - 1) {
                    val lineY = y + 12
                    appendLine("""<line x1="24" y1="$lineY" x2="${width - 24}" y2="$lineY" stroke="#181F27" stroke-width="1"/>""")
                }
            }

            val footerY = height - footerHeight + 20
            appendLine(
                """<text x="24" y="$footerY" font-family="sans-serif" font-size="9" fill="#5A6470">${escapeXml(wrapDisclaimer())}</text>"""
            )
            appendLine("</svg>")
        }

        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "battery_stats_status_${fileTimestampFmt.format(Date())}.svg")
        file.writeText(svg)
        return file
    }

    // Keeps the disclaimer to a single readable line in the fixed-width SVG footer.
    private fun wrapDisclaimer(): String {
        val text = PowerTerminology.WATTAGE_ESTIMATE_DISCLAIMER
        return if (text.length > 90) text.take(87) + "…" else text
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    fun shareUriFor(context: Context, file: File) = CsvExportBuilder.shareUriFor(context, file)
}
