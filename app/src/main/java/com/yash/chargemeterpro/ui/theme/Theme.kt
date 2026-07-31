package com.yash.chargemeterpro.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * ChargeMeter Pro is dark-first by design (see feature spec: "Dark mode as
 * the default", "AMOLED-friendly dark theme"). We deliberately do NOT wire
 * up dynamic/Material-You color extraction as the default — the instrument
 * panel palette (functional green/blue/amber accents tied to measurement
 * type) is a core part of the product's identity and shouldn't be
 * overridden by the user's wallpaper. Dynamic color is offered as an
 * explicit opt-in in Settings instead (useDynamicColor param below).
 */

private val DarkInstrumentScheme = darkColorScheme(
    primary = PhosphorGreen,
    onPrimary = InstrumentBg,
    primaryContainer = PhosphorGreenDim,
    onPrimaryContainer = White,
    secondary = VoltageBlue,
    onSecondary = White,
    tertiary = WarningAmber,
    onTertiary = InstrumentBg,
    background = InstrumentBg,
    onBackground = White,
    surface = InstrumentSurface,
    onSurface = White,
    surfaceVariant = InstrumentSurfaceRaised,
    onSurfaceVariant = PanelGray,
    outline = Hairline,
    error = CriticalRed,
    onError = White
)

// Light scheme kept available for accessibility / system-forced light mode,
// but Settings defaults theme_mode to DARK per the product spec.
private val LightInstrumentScheme = lightColorScheme(
    primary = PhosphorGreenDim,
    onPrimary = White,
    secondary = VoltageBlue,
    tertiary = WarningAmber,
    background = Color(0xFFF4F6F8),
    onBackground = Color(0xFF10151A),
    surface = White,
    onSurface = Color(0xFF10151A),
    outline = Color(0xFFD8DEE4),
    error = CriticalRed
)

@Composable
fun ChargeMeterProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalView.current.context
            if (darkTheme) dynamicDarkColorScheme(context) else lightColorScheme()
        }
        darkTheme -> DarkInstrumentScheme
        else -> LightInstrumentScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
