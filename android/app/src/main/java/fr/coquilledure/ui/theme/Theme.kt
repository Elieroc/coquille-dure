package fr.coquilledure.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Échelle de police système maximale prise en compte (au-delà, les mises en page cassent sur petits écrans). */
private const val MAX_FONT_SCALE = 1.3f

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = White,
    primaryContainer = GreenContainer,
    onPrimaryContainer = GreenOnContainer,
    secondaryContainer = GreenContainer,
    onSecondaryContainer = GreenOnContainer,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = ChipGray,
    onSurfaceVariant = OnSurfaceVariant,
    outline = OutlineGray,
    outlineVariant = OutlineGray,
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF003314),
    primaryContainer = GreenDark,
    onPrimaryContainer = Color(0xFFB7F0CC),
    secondaryContainer = GreenDark,
    onSecondaryContainer = Color(0xFFB7F0CC),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkChip,
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = DarkOutline,
    outlineVariant = DarkOutline,
)

@Composable
fun CoquilleDureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale = density.fontScale.coerceAtMost(MAX_FONT_SCALE)),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content,
        )
    }
}
