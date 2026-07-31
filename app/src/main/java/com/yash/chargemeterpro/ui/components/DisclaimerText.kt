package com.yash.chargemeterpro.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yash.chargemeterpro.ui.theme.PanelGray

/**
 * Renders one of the required disclaimer strings (see
 * domain/usecase/PowerTerminology.kt) with a small info icon. Used
 * anywhere a wattage/efficiency/health-score value is shown, per the
 * product requirement that estimates are never presented as exact
 * measurements without this context nearby.
 */
@Composable
fun DisclaimerText(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = PanelGray,
            modifier = Modifier.width(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = PanelGray
        )
    }
}
