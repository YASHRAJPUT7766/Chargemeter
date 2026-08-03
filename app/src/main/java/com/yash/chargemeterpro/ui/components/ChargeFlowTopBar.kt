package com.yash.chargemeterpro.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/**
 * The single, consistent top app bar shown on every top-level screen
 * (Home, Stats, History, Settings): the ChargeFlow launcher mark sitting
 * directly beside the "ChargeFlow" name — both left-aligned together as
 * one lockup, not split across the bar — plus three quick-action icons
 * on the right:
 *
 *  1. Theme toggle — one tap flips dark <-> light. (System-follow mode
 *     is still available as a separate, explicit choice in Settings;
 *     this quick toggle only ever lands on a concrete dark or light
 *     state, matching "one click light, one click dark".)
 *  2. Share — exports/shares the current charging snapshot (mirrors
 *     Home's live status) as a PDF or SVG.
 *  3. Battery Health — direct shortcut into the Battery Health screen,
 *     since that screen no longer has its own bottom-nav slot.
 *
 * Drill-in screens (Live Monitor, Battery Health, Session Detail, etc.)
 * use ScreenBackTopBar instead, since those need a back action rather
 * than this brand+shortcuts header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargeFlowTopBar(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onShare: () -> Unit,
    onOpenBatteryHealth: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_toolbar_logo),
                    contentDescription = "ChargeFlow logo",
                    modifier = Modifier.size(40.dp)
                )
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        text = "ChargeFlow",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onToggleTheme) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = if (isDarkTheme) "Switch to light theme" else "Switch to dark theme"
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share charging summary"
                )
            }
            IconButton(onClick = onOpenBatteryHealth) {
                Icon(
                    imageVector = Icons.Filled.FavoriteBorder,
                    contentDescription = "Open Battery Health"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}
