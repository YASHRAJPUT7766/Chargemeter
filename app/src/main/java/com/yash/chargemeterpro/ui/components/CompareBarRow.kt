package com.yash.chargemeterpro.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yash.chargemeterpro.ui.theme.Hairline
import com.yash.chargemeterpro.ui.theme.PanelGray

/**
 * A "Label" header over two horizontal capsule bars (A above B), scaled
 * against the larger of the two values so the winner reads instantly as
 * the fuller/brighter bar — used on the Compare Sessions screen in place
 * of a plain three-column text table.
 */
@Composable
fun CompareBarRow(
    label: String,
    valueA: Float?,
    valueB: Float?,
    displayA: String,
    displayB: String,
    colorA: Color,
    colorB: Color,
    modifier: Modifier = Modifier
) {
    val maxVal = maxOf(valueA ?: 0f, valueB ?: 0f).takeIf { it > 0f } ?: 1f
    val fracA = ((valueA ?: 0f) / maxVal).coerceIn(0f, 1f)
    val fracB = ((valueB ?: 0f) / maxVal).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = PanelGray)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
        MiniCompareBar(fraction = fracA, display = displayA, color = colorA)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(5.dp))
        MiniCompareBar(fraction = fracB, display = displayB, color = colorB)
    }
}

@Composable
private fun MiniCompareBar(fraction: Float, display: String, color: Color) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "compareBarFraction"
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(50))
                .background(Hairline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = animated.coerceAtLeast(0.03f))
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
        Text(
            display,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.width(56.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
