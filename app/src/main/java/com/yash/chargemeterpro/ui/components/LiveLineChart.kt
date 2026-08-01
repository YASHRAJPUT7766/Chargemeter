package com.yash.chargemeterpro.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.yash.chargemeterpro.ui.screens.livemonitor.GraphPoint
import com.yash.chargemeterpro.ui.theme.PanelGrayDim
import kotlinx.coroutines.launch

/**
 * ⚠️ VERIFY-BEFORE-SHIPPING NOTE: this file targets Vico
 * `2.0.0-beta.3` (declared in app/build.gradle.kts). Vico's Compose API
 * changed meaningfully across its 1.x → 2.x beta cycle, and beta-to-beta
 * API surface (exact function names like `rememberLineFill`, the shape
 * of `LineCartesianLayer.LineProvider.series(...)`, and axis
 * `rememberStart()`/`rememberBottom()` helpers) has shifted between
 * releases. The calls below are constructed from Vico's documented 2.0
 * beta patterns, but if Android Studio flags an unresolved reference
 * here after syncing, check the exact API for whichever beta.N you land
 * on at https://patrykandpatrick.com/vico/guide — Vico's own migration
 * guide documents each beta's breaking changes precisely. If it's faster
 * to unblock a build, an MPAndroidChart-based fallback would need no
 * such caveat (mature, stable 1.0 API) at the cost of a less
 * Compose-idiomatic integration.
 *
 * Renders one [GraphPoint] series as a live-updating line chart. Used by
 * Live Monitor (feature #4) for the Wattage/Current/Voltage/%/Temperature
 * vs Time graphs. The line color is passed in per-metric by the caller so
 * it can match the same functional color-coding used elsewhere (green =
 * power, blue = voltage, amber = current/temp) — see GraphMetric color
 * mapping in LiveMonitorScreen.kt.
 *
 * When [points] has fewer than 2 entries, shows an empty/waiting state
 * rather than an empty or broken chart — this happens naturally right
 * after charging starts, before enough samples have accumulated.
 */
@Composable
fun LiveLineChart(
    points: List<GraphPoint>,
    lineColor: Color,
    unitSuffix: String,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Waiting for enough data to draw a graph…",
                style = MaterialTheme.typography.bodyMedium,
                color = PanelGrayDim
            )
        }
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    androidx.compose.runtime.LaunchedEffect(points) {
        launch {
            modelProducer.runTransaction {
                lineSeries {
                    series(points.map { it.value })
                }
            }
        }
    }

    val lineLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            LineCartesianLayer.rememberLine(
                fill = remember(lineColor) { LineCartesianLayer.LineFill.single(fill(lineColor)) }
            )
        )
    )

    val chart = rememberCartesianChart(
        lineLayer,
        startAxis = VerticalAxis.rememberStart(),
        bottomAxis = HorizontalAxis.rememberBottom()
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(220.dp)
    )
}
