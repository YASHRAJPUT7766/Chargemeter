package com.yash.chargemeterpro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.yash.chargemeterpro.R

/**
 * Two-family type system, chosen deliberately for this subject:
 *
 *   - InstrumentMono: every numeric reading (watts, volts, mA, %, °C).
 *     Real measurement instruments render digits in a monospaced,
 *     tabular face so values don't visually jitter as digits change —
 *     a humanist sans here would undercut the "this is a measurement"
 *     framing the whole app is built around.
 *
 *   - InterUI: all interface chrome — nav labels, section headers, body
 *     copy, disclaimers. Keeps the instrument-mono treatment special
 *     rather than diluting it across the whole screen.
 *
 * Font files are expected at res/font/jetbrains_mono_*.ttf and
 * res/font/inter_*.ttf — see README.md "Fonts" section for the exact
 * files to drop in (both are open-license Google Fonts).
 */

val InstrumentMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

val InterUI = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** Reserved for the single biggest number on screen — the live Watt reading. */
val HeroReadingStyle = TextStyle(
    fontFamily = InstrumentMono,
    fontWeight = FontWeight.Bold,
    fontSize = 64.sp,
    letterSpacing = (-1).sp,
    textAlign = TextAlign.Center
)

/** Secondary readings — voltage/current/temp rows on the dashboard. */
val ReadingStyle = TextStyle(
    fontFamily = InstrumentMono,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    letterSpacing = 0.sp
)

/** Small inline readings — table cells, history list rows. */
val ReadingStyleSmall = TextStyle(
    fontFamily = InstrumentMono,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp
)

/** Units / labels that sit next to a reading (V, mA, °C, W). */
val UnitLabelStyle = TextStyle(
    fontFamily = InterUI,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    letterSpacing = 0.5.sp
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterUI,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterUI,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterUI,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterUI,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterUI,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterUI,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterUI,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = PanelGray
    ),
    labelLarge = TextStyle(
        fontFamily = InterUI,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterUI,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterUI,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)
