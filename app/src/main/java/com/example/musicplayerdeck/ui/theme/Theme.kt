package com.example.musicplayerdeck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorScheme = darkColorScheme(
    primary              = TealPrimary,
    onPrimary            = AppBackground,
    primaryContainer     = AppElevated,
    onPrimaryContainer   = TealPrimary,
    secondary            = TealPrimaryDark,
    onSecondary          = AppBackground,
    secondaryContainer   = AppCard,
    onSecondaryContainer = TextPrimary,
    tertiary             = TextMuted,
    onTertiary           = AppBackground,
    background           = AppBackground,
    surface              = AppSurface,
    surfaceVariant       = AppCard,
    onBackground         = TextPrimary,
    onSurface            = TextPrimary,
    onSurfaceVariant     = TextSecondary,
    outline              = DividerLightColor,
    outlineVariant       = DividerColor,
    scrim                = Color.Black,
    error                = Color(0xFFCF6679),
    onError              = Color.White,
    errorContainer       = Color(0xFF3B1218),
    onErrorContainer     = Color(0xFFFFB3B8),
    inverseSurface       = TextPrimary,
    inverseOnSurface     = AppBackground,
    inversePrimary       = TealPrimaryDark,
    surfaceTint          = TealPrimary,
)

@Composable
fun MusicPlayerDeckTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography  = Typography,
        content     = content
    )
}
