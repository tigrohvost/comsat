package com.comsat.audio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeMode { DARK, LIGHT, NORDIC }

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
    tertiary         = GreenNeon,
    background       = BackgroundDay,
    onBackground     = TextPrimaryDay,
    surface          = SurfaceDay,
    onSurface        = TextPrimaryDay,
    surfaceVariant   = SurfaceVarDay,
    onSurfaceVariant = TextSecDay,
    outline          = OutlineDay,
    error            = RedError
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

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.DARK }
val LocalSetTheme  = staticCompositionLocalOf<(ThemeMode) -> Unit> { {} }

@Composable
fun ComsatTheme(
    mode: ThemeMode = ThemeMode.DARK,
    onSetTheme: (ThemeMode) -> Unit = {},
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalThemeMode provides mode,
        LocalSetTheme  provides onSetTheme
    ) {
        MaterialTheme(
            colorScheme = when (mode) {
                ThemeMode.DARK   -> DarkColors
                ThemeMode.LIGHT  -> LightColors
                ThemeMode.NORDIC -> NordColors
            },
            typography = ComsatTypography,
            content = content
        )
    }
}
