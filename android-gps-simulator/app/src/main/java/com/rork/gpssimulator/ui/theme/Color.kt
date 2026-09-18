package com.rork.gpssimulator.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val BlueAccent = Color(0xFF2F6FED)
val BlueAccentDark = Color(0xFF5B93FF)
val CanvasLight = Color(0xFFF5F6F8)
val CanvasDark = Color(0xFF111318)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF1B1F26)
val TextLight = Color(0xFF111318)
val TextDark = Color(0xFFF5F6F8)
val MutedGray = Color(0xFF6B7280)
val MutedGrayDark = Color(0xFF9AA3B2)
val StatusGreen = Color(0xFF1FAA59)
val StatusRed = Color(0xFFE1483B)
val StatusAmber = Color(0xFFE08A1E)

/** Semantic colors that Material 3's scheme does not cover (status dots, map chrome). */
@Immutable
data class AppColors(
    val success: Color,
    val danger: Color,
    val warning: Color,
    val muted: Color,
    val divider: Color,
    val floatingSurface: Color,
    val scrim: Color,
    val accentSoft: Color,
)

val LightAppColors = AppColors(
    success = StatusGreen,
    danger = StatusRed,
    warning = StatusAmber,
    muted = MutedGray,
    divider = Color(0x14111318),
    floatingSurface = Color(0xFFFFFFFF),
    scrim = Color(0x33000000),
    accentSoft = Color(0xFFE8F0FE),
)

val DarkAppColors = AppColors(
    success = Color(0xFF3ECB7B),
    danger = Color(0xFFFF6B5C),
    warning = Color(0xFFF2A63B),
    muted = MutedGrayDark,
    divider = Color(0x1FFFFFFF),
    floatingSurface = Color(0xFF232830),
    scrim = Color(0x66000000),
    accentSoft = Color(0xFF1B2A44),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
