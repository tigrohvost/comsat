package com.comsat.audio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

// Append new modes: SettingsRepository persists their ordinals.
enum class ThemeMode { DARK, LIGHT, NORDIC, ORANGE }

private val DarkColors = darkColorScheme(
    primary          = CyanNeon,
    onPrimary        = Background,
    primaryContainer = CyanDim,
    secondary        = MagentaNeon,
    onSecondary      = Background,
    tertiary         = GreenNeon,
    background       = Background,
    onBackground     = TextPrimary,
    surface          = Surface,
    onSurface        = TextPrimary,
    surfaceVariant   = SurfaceVar,
    onSurfaceVariant = TextSecondary,
    outline          = Outline,
    error            = RedError,
    scrim            = Scrim
)

private val LightColors = lightColorScheme(
    primary          = CyanDay,
    onPrimary        = SurfaceDay,
    primaryContainer = SurfaceVarDay,
    secondary        = MagentaDay,
    onSecondary      = SurfaceDay,
    tertiary         = GreenDay,
    background       = BackgroundDay,
    onBackground     = TextPrimaryDay,
    surface          = SurfaceDay,
    onSurface        = TextPrimaryDay,
    surfaceVariant   = SurfaceVarDay,
    onSurfaceVariant = TextSecDay,
    outline          = OutlineDay,
    error            = ErrorDay
)

private val NordColors = darkColorScheme(
    primary          = NordPrimary,
    onPrimary        = NordBg,
    primaryContainer = NordSurface,
    secondary        = NordSecondary,
    onSecondary      = NordBg,
    tertiary         = NordTertiary,
    background       = NordBg,
    onBackground     = NordText,
    surface          = NordSurface,
    onSurface        = NordText,
    surfaceVariant   = NordSurfVar,
    onSurfaceVariant = NordTextSec,
    outline          = NordOutline,
    error            = NordError,
    scrim            = Scrim
)

private val OrangeColors = darkColorScheme(
    primary          = OrangePrimary,
    onPrimary        = OrangeBg,
    primaryContainer = OrangeSurfVar,
    onPrimaryContainer = OrangeText,
    secondary        = OrangeSecondary,
    onSecondary      = OrangeBg,
    secondaryContainer = OrangeSurfVar,
    onSecondaryContainer = OrangeText,
    tertiary         = OrangeTertiary,
    onTertiary       = OrangeBg,
    tertiaryContainer = OrangeSurfVar,
    onTertiaryContainer = OrangeText,
    background       = OrangeBg,
    onBackground     = OrangeText,
    surface          = OrangeSurface,
    onSurface        = OrangeText,
    surfaceVariant   = OrangeSurfVar,
    onSurfaceVariant = OrangeTextSec,
    surfaceTint      = OrangePrimary,
    surfaceDim       = OrangeBg,
    surfaceBright    = OrangeSurfVar,
    surfaceContainerLowest = OrangeBg,
    surfaceContainerLow = OrangeSurface,
    surfaceContainer = OrangeSurface,
    surfaceContainerHigh = OrangeSurfVar,
    surfaceContainerHighest = OrangeSurfVar,
    inverseSurface   = OrangeText,
    inverseOnSurface = OrangeBg,
    inversePrimary   = OrangeOutline,
    outline          = OrangeOutline,
    outlineVariant   = OrangeOutline,
    error            = OrangeError,
    onError          = OrangeBg,
    errorContainer   = OrangeSurfVar,
    onErrorContainer = OrangeError,
    scrim            = Scrim
)

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.DARK }
val LocalSetTheme  = staticCompositionLocalOf<(ThemeMode) -> Unit> { {} }
val LocalWarningColor = staticCompositionLocalOf { NordYellow }

@Composable
fun ComsatTheme(
    mode: ThemeMode = ThemeMode.DARK,
    onSetTheme: (ThemeMode) -> Unit = {},
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalThemeMode provides mode,
        LocalSetTheme  provides onSetTheme,
        LocalWarningColor provides when (mode) {
            ThemeMode.LIGHT -> WarningDay
            ThemeMode.ORANGE -> OrangeWarning
            else -> NordYellow
        }
    ) {
        MaterialTheme(
            colorScheme = when (mode) {
                ThemeMode.DARK   -> DarkColors
                ThemeMode.LIGHT  -> LightColors
                ThemeMode.NORDIC -> NordColors
                ThemeMode.ORANGE -> OrangeColors
            },
            typography = ComsatTypography,
            content = content
        )
    }
}
