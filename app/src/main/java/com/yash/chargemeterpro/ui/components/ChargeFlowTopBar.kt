package com.yash.chargemeterpro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yash.chargemeterpro.R
import com.yash.chargemeterpro.ui.theme.InstrumentSurface
import com.yash.chargemeterpro.ui.theme.PhosphorGreen

/**
 * The single, consistent top app bar shown on every top-level screen
 * (Home, Stats, History, Settings) — always the ChargeFlow mark plus the
 * app name, per the "every page must use the ChargeFlow logo/name in the
 * top area" requirement. Drill-in screens use ScreenBackTopBar instead
 * (see ChargeMeterNavHost.kt) since those need a back action, not a
 * fresh brand header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargeFlowTopBar(subtitle: String? = null) {
    TopAppBar(
        title = {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(color = InstrumentSurface, shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = PhosphorGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        actions = {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Text(
                    text = "ChargeFlow",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}
