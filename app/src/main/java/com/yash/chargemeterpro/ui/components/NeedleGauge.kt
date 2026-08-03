package com.yash.chargemeterpro.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yash.chargemeterpro.ui.theme.Hairline
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PanelGrayDim
import com.yash.chargemeterpro.ui.theme.ReadingStyle
import com.yash.chargemeterpro.ui.theme.UnitLabelStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * A dial-and-needle instrument gauge — a colored semicircular scale
 * (a "zone band", e.g. red -> amber -> green across the range) with a
 * pivoted pointer needle rotating to the live value, exactly like a
 * bench multimeter or an automotive gauge cluster. This is visually
 * distinct from [WattMeterGauge] (progress-arc phosphor dial, no needle)
 * and [RingMeter] (full-circle progress ring) — the three together give
 * the app variety across screens the way a real instrument panel mixes
 * dial types instead of repeating one shape everywhere.
 *
 * [zones] paints the track in colored bands (fractions of the full
 * 0f..1f range, ascending, covering the whole arc) rather than a single
 * flat track color — e.g. red 0-0.3, amber 0.3-0.7, green 0.7-1.0 for a
 * "higher is better" reading, or reversed for "higher is worse" (like
 * temperature). If null, the arc is drawn as a single [needleColor]
 * progress sweep instead (used for plain magnitude gauges like watts).
 */
@Composable
fun NeedleGauge(
    fraction: Float,
    value: String,
    unit: String? = null,
    label: String? = null,
    minLabel: String? = null,
    maxLabel: String? = null,
    zones: List<GaugeZone>? = null,
    needleColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "needleGaugeFraction"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.55f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.55f)) {
                val strokeWidth = size.minDimension * 0.16f
                val radius = (size.minDimension - strokeWidth) / 2f
                // Center sits low so the arc reads as a top semicircle, dial-cluster style.
                val center = Offset(size.width / 2f, size.height * 0.92f)
                val topLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2, radius * 2)

                val startAngle = 180f
                val sweepAngle = 180f

                if (zones != null && zones.isNotEmpty()) {
                    zones.forEach { zone ->
                        drawArc(
                            color = zone.color,
                            startAngle = startAngle + sweepAngle * zone.startFraction,
                            sweepAngle = sweepAngle * (zone.endFraction - zone.startFraction),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                    }
                } else {
                    drawArc(
                        color = Hairline,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    if (animatedFraction > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(needleColor.copy(alpha = 0.35f), needleColor)),
                            startAngle = startAngle,
                            sweepAngle = sweepAngle * animatedFraction,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Minor/major tick marks around the band
                for (i in 0..20) {
                    val t = i / 20f
                    val tickAngleDeg = startAngle + sweepAngle * t
                    val tickAngleRad = Math.toRadians(tickAngleDeg.toDouble())
                    val isMajor = i % 5 == 0
                    val outerR = radius + strokeWidth / 2f + 2.dp.toPx()
                    val innerR = outerR - (if (isMajor) 9.dp.toPx() else 5.dp.toPx())
                    val tickStroke = if (isMajor) 2.5.dp.toPx() else 1.dp.toPx()
                    val startPoint = Offset(
                        center.x + (cos(tickAngleRad) * innerR).toFloat(),
                        center.y + (sin(tickAngleRad) * innerR).toFloat()
                    )
                    val endPoint = Offset(
                        center.x + (cos(tickAngleRad) * outerR).toFloat(),
                        center.y + (sin(tickAngleRad) * outerR).toFloat()
                    )
                    drawLine(
                        color = if (isMajor) PanelGray.copy(alpha = 0.9f) else PanelGrayDim.copy(alpha = 0.6f),
                        start = startPoint,
                        end = endPoint,
                        strokeWidth = tickStroke,
                        cap = StrokeCap.Round
                    )
                }

                // Needle: pivoted line from center to near the outer band, plus a hub circle.
                val needleAngleDeg = startAngle + sweepAngle * animatedFraction
                val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
                val needleLength = radius - strokeWidth * 0.35f
                val needleTip = Offset(
                    center.x + (cos(needleAngleRad) * needleLength).toFloat(),
                    center.y + (sin(needleAngleRad) * needleLength).toFloat()
                )
                val needleTail = Offset(
                    center.x - (cos(needleAngleRad) * needleLength * 0.14f).toFloat(),
                    center.y - (sin(needleAngleRad) * needleLength * 0.14f).toFloat()
                )
                drawLine(
                    color = androidx.compose.ui.graphics.Color.White,
                    start = needleTail,
                    end = needleTip,
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(color = needleColor, radius = 7.dp.toPx(), center = center)
                drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 3.dp.toPx(), center = center)
            }

            // Value readout, positioned in the lower portion of the box (under the arc).
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = value,
                        style = ReadingStyle,
                        color = needleColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    if (unit != null) {
                        Text(text = unit, style = UnitLabelStyle, color = PanelGray, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        if (minLabel != null || maxLabel != null) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Text(minLabel ?: "", style = MaterialTheme.typography.labelSmall, color = PanelGrayDim)
                Text(maxLabel ?: "", style = MaterialTheme.typography.labelSmall, color = PanelGrayDim)
            }
        }

        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = PanelGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** A colored band on a [NeedleGauge]'s scale, spanning [startFraction]..[endFraction] of the full 0f..1f arc. */
data class GaugeZone(val startFraction: Float, val endFraction: Float, val color: Color)
