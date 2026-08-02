package de.beispiel.meintraining.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = TextPrimary,
    secondary = MenuButtonSurface,
    onSecondary = MenuButtonIcon,
    background = ScreenBackground,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = ChipBackground,
    onSurfaceVariant = TextSecondary,
    outline = OutlineColor,
    outlineVariant = OutlineColor,
    error = AccentBlue,
    onError = TextPrimary
)

/** Fest dunkles Theme ohne dynamische Farben. */
@Composable
fun MeinTrainingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
