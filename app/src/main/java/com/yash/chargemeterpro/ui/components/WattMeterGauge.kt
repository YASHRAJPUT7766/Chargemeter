package com.yash.chargemeterpro.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yash.chargemeterpro.ui.theme.HeroReadingStyle
import com.yash.chargemeterpro.ui.theme.Hairline
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PanelGrayDim
import com.yash.chargemeterpro.ui.theme.PhosphorGreen
import com.yash.chargemeterpro.ui.theme.PhosphorGreenDim
import com.yash.chargemeterpro.ui.theme.UnitLabelStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app's signature visual element: an analog-instrument-style circular
 * power gauge, not a generic Material CircularProgressIndicator. Modeled
 * on a bench power-analyzer dial — tick marks around a ~270° arc, a
 * phosphor-glow progress arc, and the live wattage rendered in monospace
 * at the center.
 *
 * [currentWatts] null renders the dial in an inert/idle state with
 * "— W" at center rather than a misleading 0.00 W.
 * [maxScaleWatts] sets what wattage maps to the full arc — defaults to a
 * sensible ceiling but callers on the Speed Test / Live Monitor screens
 * pass a value derived from the session's observed max so the needle
 * doesn't sit permanently near-empty for typical charging wattages.
 */
@Composable
fun WattMeterGauge(
    currentWatts: Double?,
    maxScaleWatts: Double = 30.0,
    voltageLabel: String? = null,
    currentLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val targetFraction = if (currentWatts != null) {
        (currentWatts / maxScaleWatts).toFloat().coerceIn(0f, 1f)
    } else 0f

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "wattMeterFraction"
    )

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val strokeWidth = size.minDimension * 0.055f
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Arc spans 270°, starting at 135° (bottom-left) — leaves a
            // gap at the bottom like a real analog meter's dead zone.
            val startAngle = 135f
            val sweepAngle = 270f

            // Track (inert full-scale arc)
            drawArc(
                color = Hairline,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Tick marks every 10% of scale
            for (i in 0..10) {
                val tickAngleDeg = startAngle + (sweepAngle * i / 10f)
                val tickAngleRad = Math.toRadians(tickAngleDeg.toDouble())
                val outerR = radius + strokeWidth * 0.75f
                val innerR = radius + strokeWidth * 0.35f
                val isMajor = i % 5 == 0
                val tickStroke = if (isMajor) 3.dp.toPx() else 1.5.dp.toPx()
                val startPoint = Offset(
                    center.x + (cos(tickAngleRad) * innerR).toFloat(),
                    center.y + (sin(tickAngleRad) * innerR).toFloat()
                )
                val endPoint = Offset(
                    center.x + (cos(tickAngleRad) * outerR).toFloat(),
                    center.y + (sin(tickAngleRad) * outerR).toFloat()
                )
                drawLine(
                    color = if (isMajor) PanelGray else PanelGrayDim,
                    start = startPoint,
                    end = endPoint,
                    strokeWidth = tickStroke,
                    cap = StrokeCap.Round
                )
            }

            // Live progress arc with phosphor glow gradient
            if (currentWatts != null) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(PhosphorGreenDim, PhosphorGreen, PhosphorGreen)
                    ),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * animatedFraction,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // Center readout
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentWatts?.let { "%.2f".format(it) } ?: "—",
                    style = HeroReadingStyle.copy(color = if (currentWatts != null) PhosphorGreen else PanelGrayDim),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "WATTS",
                    style = UnitLabelStyle,
                    color = PanelGray,
                    textAlign = TextAlign.Center
                )
                if (voltageLabel != null || currentLabel != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(voltageLabel, currentLabel).joinToString("  ·  "),
                        style = UnitLabelStyle,
                        color = PanelGrayDim,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
