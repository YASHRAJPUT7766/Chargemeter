package com.yash.chargemeterpro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yash.chargemeterpro.domain.usecase.PowerTerminology
import com.yash.chargemeterpro.ui.theme.Hairline
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PanelGrayDim

/**
 * A "label + colored pill" row for categorical or boolean readings
 * (plug type, USB-PD status, fast-charge yes/no, health) where a numeric
 * ring/gauge doesn't make sense but a plain text value still reads as
 * flat data. Keeps the same not-available convention as ReadingRow: a
 * null [value] renders as a dim "N/A" pill rather than inventing data
 * the device didn't actually report.
 */
@Composable
fun StatusBadgeRow(
    label: String,
    value: String?,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = PanelGray
        )
        val isAvailable = value != null
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (isAvailable) accentColor.copy(alpha = 0.16f) else Hairline)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = value ?: PowerTerminology.NOT_AVAILABLE,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isAvailable) accentColor else PanelGrayDim
            )
        }
    }
}
