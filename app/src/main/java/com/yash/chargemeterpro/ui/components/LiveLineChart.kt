package com.yash.chargemeterpro.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.yash.chargemeterpro.ui.screens.livemonitor.GraphPoint
import com.yash.chargemeterpro.ui.theme.PanelGrayDim

@Composable
fun LiveLineChart(
    points: List<GraphPoint>,
    lineColor: Color,
    unitSuffix: String,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Waiting for enough data to draw a graph…",
                style = MaterialTheme.typography.bodyMedium,
                color = PanelGrayDim
            )
        }
        return
    }

    val modelProducer = remember {
        CartesianChartModelProducer()
    }

    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineSeries {
                series(points.map { it.value })
            }
        }
    }

    val lineLayer = rememberLineCartesianLayer()

    val chart = rememberCartesianChart(
        lineLayer,
        startAxis = VerticalAxis.rememberStart(),
        bottomAxis = HorizontalAxis.rememberBottom()
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
    )
}
