package com.yash.chargemeterpro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.yash.chargemeterpro.domain.usecase.PowerTerminology
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.ReadingStyleSmall

/**
 * A single "Label ................ VALUE unit" row, used throughout the
 * dashboard, battery health, and history detail screens. Centralizing
 * this is what enforces the "never show a fake value" rule visually —
 * every reading in the app that might be device-unavailable should
 * render through this composable rather than a bespoke Text() call, so
 * the Not-Available fallback is applied consistently everywhere.
 */
@Composable
fun ReadingRow(
    label: String,
    value: String?,
    unit: String = "",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = PanelGray
        )
        Text(
            text = if (value != null) "$value $unit".trim() else PowerTerminology.NOT_AVAILABLE,
            style = ReadingStyleSmall,
            fontWeight = if (value != null) FontWeight.Medium else FontWeight.Normal,
            color = if (value != null) MaterialTheme.colorScheme.onSurface else PanelGray
        )
    }
}
