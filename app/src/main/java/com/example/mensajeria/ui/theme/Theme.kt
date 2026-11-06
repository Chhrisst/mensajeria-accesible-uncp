package com.example.mensajeria.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = HighContrastYellow,
    onPrimary = TextDark,
    primaryContainer = HighContrastYellowPressed,
    onPrimaryContainer = TextDark,
    secondary = AccentBlue,
    onSecondary = TextDark,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = TextPrimary
)

@Composable
fun MensajeriaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AccessibleTypography,
        content = content
    )
}
