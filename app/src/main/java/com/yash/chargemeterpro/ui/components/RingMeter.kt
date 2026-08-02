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

/**
 * A small circular progress ring for dense stat grids — the compact
 * sibling of WattMeterGauge. Same phosphor-glow-on-hairline-track visual
 * language (a thin inert track plus a colored progress arc), just sized
 * and simplified (no tick marks) for sitting several-to-a-row inside a
 * card, rather than being the single hero element of a screen.
 *
 * [fraction] is 0f..1f. [label]/[value]/[unit] render stacked in the
 * center — this is deliberately compact text, not the big HeroReadingStyle
 * used by WattMeterGauge.
 */
@Composable
fun RingMeter(
    fraction: Float,
    value: String,
    unit: String? = null,
    label: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 92.dp,
    strokeWidth: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ringMeterFraction"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val radius = (this.size.minDimension - stroke) / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)

            // Full circle track
            drawArc(
                color = Hairline,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // Progress arc, starting from the top (12 o'clock), clockwise
            if (animatedFraction > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (fraction > 0f) color else PanelGrayDim,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = PanelGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
