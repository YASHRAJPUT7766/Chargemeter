package com.yash.chargemeterpro.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yash.chargemeterpro.ui.theme.Hairline
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PanelGrayDim
import kotlin.math.cos
import kotlin.math.sin

/**
 * A [Dp]-sized analog meter — same tick marks + phosphor-glow sweep-
 * gradient arc as WattMeterGauge, but explicitly sized instead of
 * fillMaxWidth so two of these can sit side-by-side in a Row (e.g. the
 * Home screen's Watt gauge next to a Charge Counter gauge). Fills the
 * same visual "instrument dial" role at a smaller scale.
 *
 * [fraction] is 0f..1f, already computed by the caller (this component
 * doesn't know about watts/mA/volts specifically, only a progress ratio,
 * so it can back any of them).
 */
@Composable
fun CompactMeterGauge(
    fraction: Float,
    centerValue: String,
    centerUnit: String,
    gaugeColors: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    subLabel: String? = null
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "compactMeterFraction"
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = size.toPx() * 0.09f
            val radius = (this.size.minDimension - strokeWidth) / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val startAngle = 135f
            val sweepAngle = 270f

            drawArc(
                color = Hairline,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            for (i in 0..10) {
                val tickAngleDeg = startAngle + (sweepAngle * i / 10f)
                val tickAngleRad = Math.toRadians(tickAngleDeg.toDouble())
                val outerR = radius + strokeWidth * 0.7f
                val innerR = radius + strokeWidth * 0.3f
                val isMajor = i % 5 == 0
                val tickStroke = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx()
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

            if (animatedFraction > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(colors = gaugeColors),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * animatedFraction,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centerValue,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Bold,
                    color = gaugeColors.last(),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    text = centerUnit,
                    style = MaterialTheme.typography.labelSmall,
                    color = PanelGray,
                    textAlign = TextAlign.Center
                )
                if (subLabel != null) {
                    Text(
                        text = subLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = PanelGrayDim,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
