package com.rork.gpssimulator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BlueAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FE),
    onPrimaryContainer = Color(0xFF0B2B6B),
    secondary = Color(0xFF41566F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF16202C),
    error = StatusRed,
    onError = Color.White,
    errorContainer = Color(0xFFFCE4E1),
    onErrorContainer = Color(0xFF6B1A12),
    background = CanvasLight,
    onBackground = TextLight,
    surface = SurfaceLight,
    onSurface = TextLight,
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = MutedGray,
    outline = Color(0xFFC7CCD6),
    outlineVariant = Color(0xFFE2E5EB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = Color(0xFFF2F4F7),
    surfaceContainerHigh = Color(0xFFEBEEF2),
    surfaceContainerHighest = Color(0xFFE4E8ED),
)

private val DarkColorScheme = darkColorScheme(
    primary = BlueAccentDark,
    onPrimary = Color(0xFF06214F),
    primaryContainer = Color(0xFF1B2A44),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFA9BAD0),
    onSecondary = Color(0xFF14212E),
    secondaryContainer = Color(0xFF29323D),
    onSecondaryContainer = Color(0xFFDCE4EE),
    error = Color(0xFFFF6B5C),
    onError = Color(0xFF4A0F09),
    errorContainer = Color(0xFF3A1815),
    onErrorContainer = Color(0xFFFFD9D4),
    background = CanvasDark,
    onBackground = TextDark,
    surface = SurfaceDark,
    onSurface = TextDark,
    surfaceVariant = Color(0xFF262B33),
    onSurfaceVariant = MutedGrayDark,
    outline = Color(0xFF3B424C),
    outlineVariant = Color(0xFF2C323A),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF16191F),
    surfaceContainer = Color(0xFF1B1F26),
    surfaceContainerHigh = Color(0xFF232830),
    surfaceContainerHighest = Color(0xFF2B313A),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
