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
 * (Home, Stats, History, Settings): the Battery Stats launcher mark sitting
 * directly beside the "Battery Stats" name — both left-aligned together as
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
fun BatteryStatsTopBar(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onShare: () -> Unit,
    onOpenBatteryHealth: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    // Uses the transparent-background launcher-foreground
                    // artwork rather than ic_toolbar_logo, which has a
                    // black rounded-square baked directly into the PNG.
                    // That baked-in box only blended in on the dark theme
                    // by coincidence — it showed as a hard black tile
                    // behind the logo on the light theme (and anywhere
                    // else the surrounding background wasn't that exact
                    // dark shade). ic_launcher_foreground is the same
                    // artwork with a real transparent background, so it
                    // sits cleanly on whatever surface color is behind it
                    // in either theme.
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Battery Stats logo",
                    modifier = Modifier.size(48.dp)
                )
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        text = "Battery Stats",
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
