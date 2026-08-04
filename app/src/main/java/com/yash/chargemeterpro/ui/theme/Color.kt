package com.yash.chargemeterpro.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design language: precision instrument panel.
 *
 * The reference point is electrical test equipment — oscilloscopes,
 * bench multimeters, power analyzers — not a generic "dark mode app"
 * palette. Each accent color is tied to what it *measures*, not chosen
 * decoratively:
 *
 *   - Phosphor green  -> power / wattage / "the live reading"
 *   - Voltage blue     -> voltage-specific values
 *   - Warning amber     -> current & thermal values, alerts
 *   - Panel gray        -> chrome, labels, inactive UI
 *
 * This keeps color functional: a screen full of green numbers with one
 * amber spike is legible at a glance the way a real instrument panel is.
 */

// --- Base surfaces ---
val InstrumentBg = Color(0xFF0A0E12)          // App background — blue-black chassis, not pure #000
val InstrumentSurface = Color(0xFF0F1720)      // Card surface
val InstrumentSurfaceRaised = Color(0xFF141E29) // Elevated card / bottom sheet
val Hairline = Color(0xFF22303C)               // 1px bezel borders instead of shadow elevation

// --- Functional accents ---
val PhosphorGreen = Color(0xFF39FF88)          // Primary reading color — power / wattage
val PhosphorGreenDim = Color(0xFF1F8F52)       // Secondary/inactive power state
val VoltageBlue = Color(0xFF5B8DEF)            // Voltage readings
val VoltageBlueDim = Color(0xFF3A5B99)         // Secondary/inactive voltage state (same darkening ratio as PhosphorGreenDim)
val WarningAmber = Color(0xFFFF6B4A)           // Current readings, thermal, general warnings
val CriticalRed = Color(0xFFFF4757)            // Critical battery, danger states

// --- Text / chrome ---
val PanelGray = Color(0xFF8B98A5)              // Primary label/secondary text
val PanelGrayDim = Color(0xFF5A6470)           // Tertiary/disabled text
val White = Color(0xFFFFFFFF)

// --- Charging speed indicator colors ---
val SpeedFast = PhosphorGreen
val SpeedNormal = VoltageBlue
val SpeedSlow = WarningAmber
val SpeedTrickle = PanelGrayDim

// --- Graph line colors (kept distinct from the functional accents above so
//     a multi-line graph legend never collides visually with a status chip) ---
val GraphWattage = PhosphorGreen
val GraphCurrent = WarningAmber
val GraphVoltage = VoltageBlue
val GraphBatteryPct = Color(0xFFB18AFF) // violet — distinguishes % from raw electrical units
val GraphTemperature = Color(0xFFFF8A5C)
