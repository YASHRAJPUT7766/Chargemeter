package com.yash.chargemeterpro.export

import android.content.Context
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.yash.chargemeterpro.data.local.entity.ChargingSessionEntity
import com.yash.chargemeterpro.domain.usecase.PowerTerminology
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a one-page PDF charging report using iText7 — a mature, stable
 * API (unlike Vico/Glance elsewhere in this project), so this file
 * doesn't carry the same "verify against docs" caveat those do.
 *
 * Two report shapes:
 *  1. [buildSingleSessionReport] — full detail for one charging session,
 *     shareable from Session Detail.
 *  2. [buildHistoryReport] — summary table across many sessions,
 *     shareable from History.
 *
 * Every report includes the same wattage-estimate disclaimer that
 * appears in-app (PowerTerminology.WATTAGE_ESTIMATE_DISCLAIMER), so a
 * PDF handed to someone else carries the same "this is an estimate, not
 * a wall-power measurement" context the app itself always shows.
 */
object PdfExportBuilder {

    private val phosphorGreen = DeviceRgb(0x39, 0xFF, 0x88)
    private val panelGray = DeviceRgb(0x5A, 0x64, 0x70)
    private val headerBg = DeviceRgb(0x14, 0x1E, 0x29)
    private val dateFmt = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US)
    private val fileTimestampFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun buildSingleSessionReport(context: Context, session: ChargingSessionEntity): File {
        val file = outputFile(context, "chargemeter_session_${session.id}_${fileTimestampFmt.format(Date())}.pdf")
        PdfDocument(PdfWriter(file.absolutePath)).use { pdf ->
            Document(pdf).use { doc ->
                writeReportHeader(doc, "Charging Session Report")

                doc.add(Paragraph(dateFmt.format(Date(session.startTimeMillis))).setFontColor(panelGray).setFontSize(10f))
                doc.add(Paragraph(" "))

                val table = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f))).useAllAvailableWidth()
                addRow(table, "Start Battery", "${session.startBatteryPercent}%")
                addRow(table, "End Battery", session.endBatteryPercent?.let { "$it%" } ?: PowerTerminology.NOT_AVAILABLE)
                addRow(table, "Plug Type", session.plugTypeName)
                addRow(
                    table,
                    "Duration",
                    session.endTimeMillis?.let {
                        val m = (it - session.startTimeMillis) / 60000
                        "${m / 60}h ${m % 60}m"
                    } ?: PowerTerminology.NOT_AVAILABLE
                )
                addRow(table, "Average Current", session.averageCurrentMilliAmps?.let { "%.1f mA".format(it) } ?: PowerTerminology.NOT_AVAILABLE)
                addRow(table, "Average Power", session.averagePowerWatts?.let { "%.2f W".format(it) } ?: PowerTerminology.NOT_AVAILABLE)
                addRow(table, "Maximum Power", session.maxPowerWatts?.let { "%.2f W".format(it) } ?: PowerTerminology.NOT_AVAILABLE)
                addRow(table, "Maximum Current", session.maxCurrentMilliAmps?.let { "%.1f mA".format(it) } ?: PowerTerminology.NOT_AVAILABLE)
                addRow(
                    table,
                    "Temperature Range",
                    if (session.minTemperatureCelsius != null && session.maxTemperatureCelsius != null) {
                        "%.1f – %.1f °C".format(session.minTemperatureCelsius, session.maxTemperatureCelsius)
                    } else PowerTerminology.NOT_AVAILABLE
                )
                addRow(table, "Estimated Energy Delivered", session.estimatedEnergyWattHours?.let { "%.2f Wh".format(it) } ?: PowerTerminology.NOT_AVAILABLE)
                doc.add(table)

                writeDisclaimerFooter(doc)
            }
        }
        return file
    }

    /**
     * A report of the current live battery/charging status — this is what
     * the top bar's Share action builds, distinct from
     * [buildSingleSessionReport] which reports on a *completed, saved*
     * session. Live snapshot data doesn't have a session id or a fixed
     * duration yet, so this uses BatterySnapshot's raw fields directly
     * rather than a ChargingSessionEntity.
     */
    fun buildLiveStatusReport(context: Context, snapshot: com.yash.chargemeterpro.domain.model.BatterySnapshot): File {
        val file = outputFile(context, "battery_stats_status_${fileTimestampFmt.format(Date())}.pdf")
        PdfDocument(PdfWriter(file.absolutePath)).use { pdf ->
            Document(pdf).use { doc ->
                writeReportHeader(doc, "Charging Status Report")

                doc.add(Paragraph(dateFmt.format(Date(snapshot.timestampMillis))).setFontColor(panelGray).setFontSize(10f))
                doc.add(Paragraph(" "))

                val table = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f))).useAllAvailableWidth()
                addRow(table, "Battery Level", "${snapshot.batteryPercent}%")
                addRow(table, "Charging Status", snapshot.chargingStatus.name)
                addRow(table, "Plug Type", snapshot.plugType.name)
                addRow(
                    table,
                    "Voltage",
                    (snapshot.voltageMilliVolts as? com.yash.chargemeterpro.domain.model.AvailableOr.Value)
                        ?.value?.let { "%.3f V".format(it / 1000.0) } ?: PowerTerminology.NOT_AVAILABLE
                )
                addRow(
                    table,
                    "Current",
                    snapshot.currentMilliAmpsNormalized?.let { "%.1f mA".format(it) } ?: PowerTerminology.NOT_AVAILABLE
                )
                addRow(
                    table,
                    "Estimated Power",
                    snapshot.batteryInputPowerWatts?.let { "%.2f W".format(it) } ?: PowerTerminology.NOT_AVAILABLE
                )
                addRow(
                    table,
                    "Temperature",
                    (snapshot.temperatureCelsius as? com.yash.chargemeterpro.domain.model.AvailableOr.Value)
                        ?.value?.let { "%.1f °C".format(it) } ?: PowerTerminology.NOT_AVAILABLE
                )
                addRow(table, "Health", snapshot.health.name)
                addRow(table, "Technology", snapshot.technology.orNull() ?: PowerTerminology.NOT_AVAILABLE)
                doc.add(table)

                writeDisclaimerFooter(doc)
            }
        }
        return file
    }

    fun buildHistoryReport(context: Context, sessions: List<ChargingSessionEntity>): File {
        val file = outputFile(context, "chargemeter_history_${fileTimestampFmt.format(Date())}.pdf")
        PdfDocument(PdfWriter(file.absolutePath)).use { pdf ->
            Document(pdf).use { doc ->
                writeReportHeader(doc, "Charging History Report")
                doc.add(Paragraph("${sessions.size} sessions").setFontColor(panelGray).setFontSize(10f))
                doc.add(Paragraph(" "))

                val table = Table(UnitValue.createPercentArray(floatArrayOf(2f, 1f, 1f, 1f, 1f))).useAllAvailableWidth()
                listOf("Date", "Start%", "End%", "Avg W", "Max W").forEach { header ->
                    table.addHeaderCell(
                        Cell().add(Paragraph(header).setBold().setFontSize(9f))
                            .setBackgroundColor(headerBg).setFontColor(phosphorGreen)
                    )
                }
                sessions.forEach { s ->
                    table.addCell(cellText(dateFmt.format(Date(s.startTimeMillis))))
                    table.addCell(cellText("${s.startBatteryPercent}%"))
                    table.addCell(cellText(s.endBatteryPercent?.let { "$it%" } ?: "—"))
                    table.addCell(cellText(s.averagePowerWatts?.let { "%.1f".format(it) } ?: "—"))
                    table.addCell(cellText(s.maxPowerWatts?.let { "%.1f".format(it) } ?: "—"))
                }
                doc.add(table)

                writeDisclaimerFooter(doc)
            }
        }
        return file
    }

    private fun writeReportHeader(doc: Document, title: String) {
        doc.add(Paragraph("Battery Stats").setFontColor(phosphorGreen).setBold().setFontSize(11f))
        doc.add(Paragraph(title).setBold().setFontSize(20f))
    }

    private fun writeDisclaimerFooter(doc: Document) {
        doc.add(Paragraph(" "))
        doc.add(
            Paragraph(PowerTerminology.WATTAGE_ESTIMATE_DISCLAIMER)
                .setFontColor(panelGray)
                .setFontSize(8f)
                .setTextAlignment(TextAlignment.LEFT)
        )
    }

    private fun addRow(table: Table, label: String, value: String) {
        table.addCell(Cell().add(Paragraph(label).setFontColor(panelGray).setFontSize(10f)).setBorder(null))
        table.addCell(Cell().add(Paragraph(value).setBold().setFontSize(10f)).setBorder(null))
    }

    private fun cellText(text: String) = Cell().add(Paragraph(text).setFontSize(9f))

    private fun outputFile(context: Context, filename: String): File {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(exportsDir, filename)
    }

    fun shareUriFor(context: Context, file: File) = CsvExportBuilder.shareUriFor(context, file)
}
