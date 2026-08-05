package com.yash.chargemeterpro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yash.chargemeterpro.ui.theme.Hairline
import com.yash.chargemeterpro.ui.theme.InstrumentSurfaceRaised
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PanelGrayDim
import kotlin.math.roundToInt

/**
 * A trend line graph — smoothed line + soft area fill under a live or
 * historical value series, plus a min/max reference and a highlighted
 * latest point. This is the app's line-chart primitive, used for
 * "value vs time" trends (Live Monitor's selected metric, a session's
 * power-over-time on Session Detail, drain history on Statistics) where
 * a ring/needle communicates "current magnitude" well but a shape over
 * time communicates "how it's been moving" better.
 *
 * Supports scrubbing: press-and-drag (or tap) anywhere along the curve
 * to inspect the exact value/label at that point, the way a stock chart
 * lets you slide a finger across history. Disabled automatically when
 * there's nothing meaningful to scrub (fewer than 2 points) or when the
 * caller opts out via [scrubEnabled] (used for small glanceable strips
 * like the Home screen mini trend, where a floating tooltip would be
 * cramped).
 */
@Composable
fun SparklineGraph(
    values: List<Float>,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 120.dp,
    label: String? = null,
    valueSuffix: String = "",
    showMinMax: Boolean = true,
    scrubEnabled: Boolean = true,
    /** Optional per-point x-axis captions (e.g. time-of-day or date), shown in the scrub tooltip when present. Must match values.size if provided. */
    pointLabels: List<String>? = null
) {
    var scrubIndex by remember(values) { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = PanelGray)
                if (values.isNotEmpty()) {
                    Text(
                        "%.1f%s".format(values.last(), valueSuffix),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        }

        Box(modifier = Modifier.fillMaxWidth().height(height)) {
            if (values.size < 2) {
                Box(modifier = Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
                    Text(
                        "Collecting data…",
                        style = MaterialTheme.typography.labelMedium,
                        color = PanelGrayDim
                    )
                }
            } else {
                val canScrub = scrubEnabled && values.size >= 2
                var canvasWidthPx by remember { mutableStateOf(0f) }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .onGloballyPositioned { coords -> canvasWidthPx = coords.size.width.toFloat() }
                        .then(
                            if (canScrub) {
                                Modifier
                                    .pointerInput(values) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val step = canvasWidthPx / (values.size - 1).toFloat()
                                                val idx = (offset.x / step).roundToInt().coerceIn(0, values.size - 1)
                                                if (scrubIndex != idx) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                scrubIndex = idx
                                            },
                                            onDrag = { change, _ ->
                                                val step = canvasWidthPx / (values.size - 1).toFloat()
                                                val idx = (change.position.x / step).roundToInt().coerceIn(0, values.size - 1)
                                                if (scrubIndex != idx) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                scrubIndex = idx
                                            },
                                            onDragEnd = { scrubIndex = null },
                                            onDragCancel = { scrubIndex = null }
                                        )
                                    }
                                    .pointerInput(values) {
                                        detectTapGestures(
                                            onPress = { offset ->
                                                val step = canvasWidthPx / (values.size - 1).toFloat()
                                                val idx = (offset.x / step).roundToInt().coerceIn(0, values.size - 1)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                scrubIndex = idx
                                                tryAwaitRelease()
                                                scrubIndex = null
                                            }
                                        )
                                    }
                            } else Modifier
                        )
                ) {
                    val minV = values.min()
                    val maxV = values.max()
                    val range = (maxV - minV).takeIf { it > 0.0001f } ?: 1f
                    val topPad = 12.dp.toPx()
                    val bottomPad = 4.dp.toPx()
                    val usableHeight = size.height - topPad - bottomPad
                    val stepX = size.width / (values.size - 1).toFloat()

                    fun pointAt(i: Int): Offset {
                        val x = stepX * i
                        val normalized = (values[i] - minV) / range
                        val y = topPad + usableHeight - (normalized * usableHeight)
                        return Offset(x, y)
                    }

                    // Faint horizontal reference gridlines (3 rows)
                    for (row in 0..2) {
                        val y = topPad + usableHeight * (row / 2f)
                        drawLine(
                            color = Hairline.copy(alpha = 0.6f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))
                        )
                    }

                    val linePath = Path()
                    val fillPath = Path()
                    linePath.moveTo(pointAt(0).x, pointAt(0).y)
                    fillPath.moveTo(pointAt(0).x, size.height)
                    fillPath.lineTo(pointAt(0).x, pointAt(0).y)

                    for (i in 1 until values.size) {
                        val prev = pointAt(i - 1)
                        val curr = pointAt(i)
                        val midX = (prev.x + curr.x) / 2f
                        linePath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                        fillPath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    }
                    fillPath.lineTo(pointAt(values.size - 1).x, size.height)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.02f)),
                            startY = 0f,
                            endY = size.height
                        )
                    )
                    drawPath(
                        path = linePath,
                        color = color,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    val activeScrub = scrubIndex
                    if (activeScrub != null) {
                        // Vertical scrub line + highlighted point at the scrubbed index
                        val scrubPoint = pointAt(activeScrub)
                        drawLine(
                            color = color.copy(alpha = 0.5f),
                            start = Offset(scrubPoint.x, topPad),
                            end = Offset(scrubPoint.x, size.height),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))
                        )
                        drawCircle(color = color.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = scrubPoint)
                        drawCircle(color = color, radius = 5.dp.toPx(), center = scrubPoint)
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = scrubPoint)
                    } else {
                        // Latest-point highlight (only when not actively scrubbing)
                        val last = pointAt(values.size - 1)
                        drawCircle(color = color.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = last)
                        drawCircle(color = color, radius = 4.dp.toPx(), center = last)
                        drawCircle(color = Color.White, radius = 1.6.dp.toPx(), center = last)
                    }
                }

                // Floating tooltip showing the exact value (and optional label) at the scrubbed point
                val activeScrub = scrubIndex
                if (activeScrub != null && canvasWidthPx > 0f) {
                    val fraction = if (values.size > 1) activeScrub / (values.size - 1).toFloat() else 0f
                    val xDp = with(androidx.compose.ui.platform.LocalDensity.current) { (canvasWidthPx * fraction).toDp() }
                    val tooltipWidth = 108.dp
                    val clampedX = xDp - (tooltipWidth / 2)

                    Box(
                        modifier = Modifier
                            .offset(x = clampedX, y = (-4).dp)
                            .width(tooltipWidth)
                            .background(InstrumentSurfaceRaised, RoundedCornerShape(8.dp))
                            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "%.1f%s".format(values[activeScrub], valueSuffix),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = color,
                                fontSize = 14.sp
                            )
                            val caption = pointLabels?.getOrNull(activeScrub)
                            if (caption != null) {
                                Text(
                                    caption,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PanelGrayDim,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showMinMax && values.size >= 2) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Text(
                    "Min %.1f%s".format(values.min(), valueSuffix),
                    style = MaterialTheme.typography.labelSmall,
                    color = PanelGrayDim
                )
                Text(
                    "Max %.1f%s".format(values.max(), valueSuffix),
                    style = MaterialTheme.typography.labelSmall,
                    color = PanelGrayDim
                )
            }
        }
    }
}
