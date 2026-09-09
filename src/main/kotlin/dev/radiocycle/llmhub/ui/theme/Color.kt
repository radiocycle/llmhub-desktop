package dev.radiocycle.llmhub.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Expressive-leaning palette: a saturated violet primary carried by a warm coral secondary and a
 * mint tertiary, so tool cards, provider badges and rotation notices stay distinguishable.
 */
private val Violet80 = Color(0xFFD3BBFF)
private val Violet60 = Color(0xFF9A6BFF)
private val Violet40 = Color(0xFF6B3FD4)
private val Violet30 = Color(0xFF52239E)
private val Violet20 = Color(0xFF3A0F79)
private val Violet10 = Color(0xFF23004D)

private val Coral80 = Color(0xFFFFB4C0)
private val Coral40 = Color(0xFFB3355A)
private val Coral30 = Color(0xFF8E1F43)
private val Coral10 = Color(0xFF3E0018)

private val Mint80 = Color(0xFF7EE0C4)
private val Mint40 = Color(0xFF00695C)
private val Mint30 = Color(0xFF00504A)
private val Mint10 = Color(0xFF00201C)

val LightColors = lightColorScheme(
    primary = Violet40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Violet10,
    inversePrimary = Violet80,
    secondary = Coral40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E1),
    onSecondaryContainer = Coral10,
    tertiary = Mint40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB8F2E1),
    onTertiaryContainer = Mint10,
    background = Color(0xFFFDF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE9E0EB),
    onSurfaceVariant = Color(0xFF4A454E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F1FA),
    surfaceContainer = Color(0xFFF2ECF4),
    surfaceContainerHigh = Color(0xFFECE6EE),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    outline = Color(0xFF7B757F),
    outlineVariant = Color(0xFFCCC4CF),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

val DarkColors = darkColorScheme(
    primary = Violet80,
    onPrimary = Violet20,
    primaryContainer = Violet30,
    onPrimaryContainer = Color(0xFFEADDFF),
    inversePrimary = Violet40,
    secondary = Coral80,
    onSecondary = Color(0xFF5E1132),
    secondaryContainer = Coral30,
    onSecondaryContainer = Color(0xFFFFD9E1),
    tertiary = Mint80,
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Mint30,
    onTertiaryContainer = Color(0xFFB8F2E1),
    background = Color(0xFF131218),
    onBackground = Color(0xFFE7E0E8),
    surface = Color(0xFF131218),
    onSurface = Color(0xFFE7E0E8),
    surfaceVariant = Color(0xFF4A454E),
    onSurfaceVariant = Color(0xFFCCC4CF),
    surfaceContainerLowest = Color(0xFF0E0D12),
    surfaceContainerLow = Color(0xFF1B1A20),
    surfaceContainer = Color(0xFF1F1E25),
    surfaceContainerHigh = Color(0xFF2A2830),
    surfaceContainerHighest = Color(0xFF35323B),
    outline = Color(0xFF958E99),
    outlineVariant = Color(0xFF4A454E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)
