package dev.radiocycle.llmhub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.radiocycle.llmhub.data.model.AppTheme

// =========================================================================================
// 1. DEFAULT (Violet / Iris)
// =========================================================================================
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

val LightColors: ColorScheme = lightColorScheme(
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

val DarkColors: ColorScheme = darkColorScheme(
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

// =========================================================================================
// 2. CATPPUCCIN (Mocha / Latte)
// =========================================================================================
val CatppuccinDarkColors = darkColorScheme(
    primary = Color(0xFFCBA6F7),       // Mauve
    onPrimary = Color(0xFF11111B),
    primaryContainer = Color(0xFF313244),
    onPrimaryContainer = Color(0xFFCBA6F7),
    inversePrimary = Color(0xFF8839EF),
    secondary = Color(0xFFF5C2E7),     // Pink
    onSecondary = Color(0xFF11111B),
    secondaryContainer = Color(0xFF45475A),
    onSecondaryContainer = Color(0xFFF5C2E7),
    tertiary = Color(0xFF94E2D5),      // Teal
    onTertiary = Color(0xFF11111B),
    tertiaryContainer = Color(0xFF313244),
    onTertiaryContainer = Color(0xFF94E2D5),
    background = Color(0xFF1E1E2E),    // Base
    onBackground = Color(0xFFCDD6F4),
    surface = Color(0xFF1E1E2E),
    onSurface = Color(0xFFCDD6F4),
    surfaceVariant = Color(0xFF313244),
    onSurfaceVariant = Color(0xFFA6ADC8),
    surfaceContainerLowest = Color(0xFF11111B), // Crust
    surfaceContainerLow = Color(0xFF181825),    // Mantle
    surfaceContainer = Color(0xFF1E1E2E),       // Base
    surfaceContainerHigh = Color(0xFF262638),
    surfaceContainerHighest = Color(0xFF313244), // Surface0
    outline = Color(0xFF585B70),
    outlineVariant = Color(0xFF45475A),
    error = Color(0xFFF38BA8),
    onError = Color(0xFF11111B),
    errorContainer = Color(0xFF45222E),
    onErrorContainer = Color(0xFFF38BA8),
)

val CatppuccinLightColors = lightColorScheme(
    primary = Color(0xFF8839EF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DEF8),
    onPrimaryContainer = Color(0xFF300060),
    secondary = Color(0xFFEA76CB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD8E4),
    onSecondaryContainer = Color(0xFF3B002E),
    tertiary = Color(0xFF179299),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD0F0EE),
    onTertiaryContainer = Color(0xFF002022),
    background = Color(0xFFEFF1F5),
    onBackground = Color(0xFF4C4F69),
    surface = Color(0xFFEFF1F5),
    onSurface = Color(0xFF4C4F69),
    surfaceVariant = Color(0xFFE6E9EF),
    onSurfaceVariant = Color(0xFF6C6F85),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F7FA),
    surfaceContainer = Color(0xFFEFF1F5),
    surfaceContainerHigh = Color(0xFFE6E9EF),
    surfaceContainerHighest = Color(0xFFDCE0E8),
    outline = Color(0xFF9CA0B0),
    outlineVariant = Color(0xFFCCD0DA),
    error = Color(0xFFD20F39),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// =========================================================================================
// 3. NORD (Polar Night / Snow Storm)
// =========================================================================================
val NordDarkColors = darkColorScheme(
    primary = Color(0xFF88C0D0),       // Frost Cyan
    onPrimary = Color(0xFF2E3440),
    primaryContainer = Color(0xFF3B4252),
    onPrimaryContainer = Color(0xFFECEFF4),
    secondary = Color(0xFF81A1C1),     // Frost Blue
    onSecondary = Color(0xFF2E3440),
    secondaryContainer = Color(0xFF434C5E),
    onSecondaryContainer = Color(0xFFECEFF4),
    tertiary = Color(0xFF8FBCBB),      // Frost Green/Teal
    onTertiary = Color(0xFF2E3440),
    tertiaryContainer = Color(0xFF3B4252),
    onTertiaryContainer = Color(0xFF8FBCBB),
    background = Color(0xFF2E3440),
    onBackground = Color(0xFFECEFF4),
    surface = Color(0xFF2E3440),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF3B4252),
    onSurfaceVariant = Color(0xFFD8DEE9),
    surfaceContainerLowest = Color(0xFF242933),
    surfaceContainerLow = Color(0xFF292E39),
    surfaceContainer = Color(0xFF2E3440),
    surfaceContainerHigh = Color(0xFF3B4252),
    surfaceContainerHighest = Color(0xFF434C5E),
    outline = Color(0xFF4C566A),
    outlineVariant = Color(0xFF3B4252),
    error = Color(0xFFBF616A),
    onError = Color(0xFF2E3440),
    errorContainer = Color(0xFF4C272C),
    onErrorContainer = Color(0xFFBF616A),
)

val NordLightColors = lightColorScheme(
    primary = Color(0xFF5E81AC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8DEE9),
    onPrimaryContainer = Color(0xFF2E3440),
    secondary = Color(0xFF81A1C1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E9F0),
    onSecondaryContainer = Color(0xFF2E3440),
    tertiary = Color(0xFF88C0D0),
    onTertiary = Color(0xFF2E3440),
    tertiaryContainer = Color(0xFFE5E9F0),
    onTertiaryContainer = Color(0xFF2E3440),
    background = Color(0xFFECEFF4),
    onBackground = Color(0xFF2E3440),
    surface = Color(0xFFECEFF4),
    onSurface = Color(0xFF2E3440),
    surfaceVariant = Color(0xFFE5E9F0),
    onSurfaceVariant = Color(0xFF4C566A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F6F9),
    surfaceContainer = Color(0xFFECEFF4),
    surfaceContainerHigh = Color(0xFFE5E9F0),
    surfaceContainerHighest = Color(0xFFD8DEE9),
    outline = Color(0xFF98A2B3),
    outlineVariant = Color(0xFFD8DEE9),
    error = Color(0xFFBF616A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// =========================================================================================
// 4. DRACULA
// =========================================================================================
val DraculaDarkColors = darkColorScheme(
    primary = Color(0xFFBD93F9),       // Purple
    onPrimary = Color(0xFF21222C),
    primaryContainer = Color(0xFF44475A),
    onPrimaryContainer = Color(0xFFF8F8F2),
    secondary = Color(0xFFFF79C6),     // Pink
    onSecondary = Color(0xFF21222C),
    secondaryContainer = Color(0xFF6272A4),
    onSecondaryContainer = Color(0xFFF8F8F2),
    tertiary = Color(0xFF8BE9FD),      // Cyan
    onTertiary = Color(0xFF21222C),
    tertiaryContainer = Color(0xFF343746),
    onTertiaryContainer = Color(0xFF8BE9FD),
    background = Color(0xFF282A36),
    onBackground = Color(0xFFF8F8F2),
    surface = Color(0xFF282A36),
    onSurface = Color(0xFFF8F8F2),
    surfaceVariant = Color(0xFF343746),
    onSurfaceVariant = Color(0xFFBFBFBF),
    surfaceContainerLowest = Color(0xFF191A21),
    surfaceContainerLow = Color(0xFF21222C),
    surfaceContainer = Color(0xFF282A36),
    surfaceContainerHigh = Color(0xFF343746),
    surfaceContainerHighest = Color(0xFF44475A),
    outline = Color(0xFF6272A4),
    outlineVariant = Color(0xFF44475A),
    error = Color(0xFFFF5555),
    onError = Color(0xFF21222C),
    errorContainer = Color(0xFF561A1D),
    onErrorContainer = Color(0xFFFF5555),
)

val DraculaLightColors = lightColorScheme(
    primary = Color(0xFF6272A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E5F0),
    onPrimaryContainer = Color(0xFF282A36),
    secondary = Color(0xFFBD93F9),
    onSecondary = Color(0xFF282A36),
    secondaryContainer = Color(0xFFF1E9FA),
    onSecondaryContainer = Color(0xFF282A36),
    tertiary = Color(0xFFFF79C6),
    onTertiary = Color(0xFF282A36),
    tertiaryContainer = Color(0xFFFEE6F4),
    onTertiaryContainer = Color(0xFF282A36),
    background = Color(0xFFF8F8F2),
    onBackground = Color(0xFF282A36),
    surface = Color(0xFFF8F8F2),
    onSurface = Color(0xFF282A36),
    surfaceVariant = Color(0xFFEBEBE3),
    onSurfaceVariant = Color(0xFF6272A4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3ED),
    surfaceContainer = Color(0xFFF8F8F2),
    surfaceContainerHigh = Color(0xFFEBEBE3),
    surfaceContainerHighest = Color(0xFFDEDECF),
    outline = Color(0xFF999BB3),
    outlineVariant = Color(0xFFD6D6CF),
    error = Color(0xFFFF5555),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// =========================================================================================
// 5. TOKYO NIGHT
// =========================================================================================
val TokyoNightDarkColors = darkColorScheme(
    primary = Color(0xFF7AA2F7),       // Tokyo Blue
    onPrimary = Color(0xFF15161E),
    primaryContainer = Color(0xFF24283B),
    onPrimaryContainer = Color(0xFFC0CAF5),
    secondary = Color(0xFFBB9AF7),     // Tokyo Magenta
    onSecondary = Color(0xFF15161E),
    secondaryContainer = Color(0xFF2F3549),
    onSecondaryContainer = Color(0xFFC0CAF5),
    tertiary = Color(0xFF7DCFFF),      // Tokyo Cyan
    onTertiary = Color(0xFF15161E),
    tertiaryContainer = Color(0xFF1F2335),
    onTertiaryContainer = Color(0xFF7DCFFF),
    background = Color(0xFF1A1B26),
    onBackground = Color(0xFFC0CAF5),
    surface = Color(0xFF1A1B26),
    onSurface = Color(0xFFC0CAF5),
    surfaceVariant = Color(0xFF24283B),
    onSurfaceVariant = Color(0xFF9AA5CE),
    surfaceContainerLowest = Color(0xFF13141C),
    surfaceContainerLow = Color(0xFF16161E),
    surfaceContainer = Color(0xFF1A1B26),
    surfaceContainerHigh = Color(0xFF24283B),
    surfaceContainerHighest = Color(0xFF2F3549),
    outline = Color(0xFF565F89),
    outlineVariant = Color(0xFF2F3549),
    error = Color(0xFFF7768E),
    onError = Color(0xFF15161E),
    errorContainer = Color(0xFF4D1D2B),
    onErrorContainer = Color(0xFFF7768E),
)

val TokyoNightLightColors = lightColorScheme(
    primary = Color(0xFF3760BF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0DCF8),
    onPrimaryContainer = Color(0xFF1A1B26),
    secondary = Color(0xFF9854F1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFE4FD),
    onSecondaryContainer = Color(0xFF1A1B26),
    tertiary = Color(0xFF007197),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD2EFF9),
    onTertiaryContainer = Color(0xFF1A1B26),
    background = Color(0xFFE1E2E7),
    onBackground = Color(0xFF24283B),
    surface = Color(0xFFE1E2E7),
    onSurface = Color(0xFF24283B),
    surfaceVariant = Color(0xFFD5D6DB),
    onSurfaceVariant = Color(0xFF6172B0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFE9EAEF),
    surfaceContainer = Color(0xFFE1E2E7),
    surfaceContainerHigh = Color(0xFFD5D6DB),
    surfaceContainerHighest = Color(0xFFC8C9CE),
    outline = Color(0xFF8990B3),
    outlineVariant = Color(0xFFC8C9CE),
    error = Color(0xFFF7768E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// =========================================================================================
// 6. GRUVBOX
// =========================================================================================
val GruvboxDarkColors = darkColorScheme(
    primary = Color(0xFFFE8019),       // Orange
    onPrimary = Color(0xFF1D2021),
    primaryContainer = Color(0xFF3C3836),
    onPrimaryContainer = Color(0xFFEBDBB2),
    secondary = Color(0xFFFABD2F),     // Yellow
    onSecondary = Color(0xFF1D2021),
    secondaryContainer = Color(0xFF504945),
    onSecondaryContainer = Color(0xFFEBDBB2),
    tertiary = Color(0xFF8EC07C),      // Aqua
    onTertiary = Color(0xFF1D2021),
    tertiaryContainer = Color(0xFF3C3836),
    onTertiaryContainer = Color(0xFF8EC07C),
    background = Color(0xFF282828),
    onBackground = Color(0xFFEBDBB2),
    surface = Color(0xFF282828),
    onSurface = Color(0xFFEBDBB2),
    surfaceVariant = Color(0xFF3C3836),
    onSurfaceVariant = Color(0xFFD5C4A1),
    surfaceContainerLowest = Color(0xFF1D2021),
    surfaceContainerLow = Color(0xFF242424),
    surfaceContainer = Color(0xFF282828),
    surfaceContainerHigh = Color(0xFF3C3836),
    surfaceContainerHighest = Color(0xFF504945),
    outline = Color(0xFF665C54),
    outlineVariant = Color(0xFF3C3836),
    error = Color(0xFFFB4934),
    onError = Color(0xFF1D2021),
    errorContainer = Color(0xFF4E1C18),
    onErrorContainer = Color(0xFFFB4934),
)

val GruvboxLightColors = lightColorScheme(
    primary = Color(0xFFAF3A03),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2E5BC),
    onPrimaryContainer = Color(0xFF282828),
    secondary = Color(0xFFB57614),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBDBB2),
    onSecondaryContainer = Color(0xFF282828),
    tertiary = Color(0xFF427B58),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0EAD8),
    onTertiaryContainer = Color(0xFF282828),
    background = Color(0xFFFBF1C7),
    onBackground = Color(0xFF282828),
    surface = Color(0xFFFBF1C7),
    onSurface = Color(0xFF282828),
    surfaceVariant = Color(0xFFF2E5BC),
    onSurfaceVariant = Color(0xFF504945),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6ECC0),
    surfaceContainer = Color(0xFFFBF1C7),
    surfaceContainerHigh = Color(0xFFF2E5BC),
    surfaceContainerHighest = Color(0xFFEBDBB2),
    outline = Color(0xFF928374),
    outlineVariant = Color(0xFFD5C4A1),
    error = Color(0xFFCC241D),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// =========================================================================================
// 7. PURE BLACK (OLED)
// =========================================================================================
val OledDarkColors = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF251A3E),
    onPrimaryContainer = Color(0xFFDDD6FE),
    secondary = Color(0xFFF472B6),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF381A2A),
    onSecondaryContainer = Color(0xFFFCE7F3),
    tertiary = Color(0xFF34D399),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF142B22),
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF242424),
    outline = Color(0xFF374151),
    outlineVariant = Color(0xFF1F2937),
    error = Color(0xFFF87171),
    onError = Color.Black,
    errorContainer = Color(0xFF3E1A1A),
    onErrorContainer = Color(0xFFF87171),
)

val OledLightColors = lightColorScheme(
    primary = Color(0xFF1F2937),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E7EB),
    onPrimaryContainer = Color(0xFF111827),
    secondary = Color(0xFF4B5563),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3F4F6),
    onSecondaryContainer = Color(0xFF111827),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF065F46),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF4B5563),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerHigh = Color(0xFFE5E7EB),
    surfaceContainerHighest = Color(0xFFD1D5DB),
    outline = Color(0xFF6B7280),
    outlineVariant = Color(0xFFE5E7EB),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
)

// =========================================================================================
// 8. EMERALD (Forest)
// =========================================================================================
val EmeraldDarkColors = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF022C22),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF6EE7B7),
    onSecondary = Color(0xFF022C22),
    secondaryContainer = Color(0xFF047857),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFF5EEAD4),
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = Color(0xFF0B3F38),
    onTertiaryContainer = Color(0xFF99F6E4),
    background = Color(0xFF061410),
    onBackground = Color(0xFFECFDF5),
    surface = Color(0xFF061410),
    onSurface = Color(0xFFECFDF5),
    surfaceVariant = Color(0xFF0E251F),
    onSurfaceVariant = Color(0xFFA3CFC4),
    surfaceContainerLowest = Color(0xFF030B09),
    surfaceContainerLow = Color(0xFF091C17),
    surfaceContainer = Color(0xFF0E251F),
    surfaceContainerHigh = Color(0xFF143028),
    surfaceContainerHighest = Color(0xFF1C3D34),
    outline = Color(0xFF2C564B),
    outlineVariant = Color(0xFF143028),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF491818),
    onErrorContainer = Color(0xFFF87171),
)

val EmeraldLightColors = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F3D0),
    onPrimaryContainer = Color(0xFF022C22),
    secondary = Color(0xFF10B981),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = Color(0xFF0D9488),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF115E59),
    background = Color(0xFFF0FDF4),
    onBackground = Color(0xFF064E3B),
    surface = Color(0xFFF0FDF4),
    onSurface = Color(0xFF064E3B),
    surfaceVariant = Color(0xFFDCFCE7),
    onSurfaceVariant = Color(0xFF047857),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5FDF7),
    surfaceContainer = Color(0xFFF0FDF4),
    surfaceContainerHigh = Color(0xFFDCFCE7),
    surfaceContainerHighest = Color(0xFFBBF7D0),
    outline = Color(0xFF34D399),
    outlineVariant = Color(0xFFDCFCE7),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
)

// =========================================================================================
// 9. SUNSET (Amber)
// =========================================================================================
val SunsetDarkColors = darkColorScheme(
    primary = Color(0xFFFB923C),
    onPrimary = Color(0xFF431407),
    primaryContainer = Color(0xFF7C2D12),
    onPrimaryContainer = Color(0xFFFFEDD5),
    secondary = Color(0xFFF87171),
    onSecondary = Color(0xFF450A0A),
    secondaryContainer = Color(0xFF7F1D1D),
    onSecondaryContainer = Color(0xFFFEE2E2),
    tertiary = Color(0xFFFACC15),
    onTertiary = Color(0xFF422006),
    tertiaryContainer = Color(0xFF713F12),
    onTertiaryContainer = Color(0xFFFEF08A),
    background = Color(0xFF150F0B),
    onBackground = Color(0xFFFED7AA),
    surface = Color(0xFF150F0B),
    onSurface = Color(0xFFFED7AA),
    surfaceVariant = Color(0xFF251C15),
    onSurfaceVariant = Color(0xFFFDBA74),
    surfaceContainerLowest = Color(0xFF0C0806),
    surfaceContainerLow = Color(0xFF1D1510),
    surfaceContainer = Color(0xFF251C15),
    surfaceContainerHigh = Color(0xFF30241C),
    surfaceContainerHighest = Color(0xFF3D2F24),
    outline = Color(0xFF604938),
    outlineVariant = Color(0xFF30241C),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF491818),
    onErrorContainer = Color(0xFFF87171),
)

val SunsetLightColors = lightColorScheme(
    primary = Color(0xFFEA580C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = Color(0xFF431407),
    secondary = Color(0xFFDC2626),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEE2E2),
    onSecondaryContainer = Color(0xFF450A0A),
    tertiary = Color(0xFFCA8A04),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF08A),
    onTertiaryContainer = Color(0xFF422006),
    background = Color(0xFFFFF7ED),
    onBackground = Color(0xFF7C2D12),
    surface = Color(0xFFFFF7ED),
    onSurface = Color(0xFF7C2D12),
    surfaceVariant = Color(0xFFFFEDD5),
    onSurfaceVariant = Color(0xFF9A3412),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFBF7),
    surfaceContainer = Color(0xFFFFF7ED),
    surfaceContainerHigh = Color(0xFFFFEDD5),
    surfaceContainerHighest = Color(0xFFFED7AA),
    outline = Color(0xFFFB923C),
    outlineVariant = Color(0xFFFFEDD5),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
)

// =========================================================================================
// 10. SOLARIZED
// =========================================================================================
val SolarizedDarkColors = darkColorScheme(
    primary = Color(0xFF268BD2),
    onPrimary = Color(0xFF002B36),
    primaryContainer = Color(0xFF073642),
    onPrimaryContainer = Color(0xFF93A1A1),
    secondary = Color(0xFF2AA198),
    onSecondary = Color(0xFF002B36),
    secondaryContainer = Color(0xFF094352),
    onSecondaryContainer = Color(0xFF93A1A1),
    tertiary = Color(0xFF859900),
    onTertiary = Color(0xFF002B36),
    tertiaryContainer = Color(0xFF073642),
    onTertiaryContainer = Color(0xFF859900),
    background = Color(0xFF002B36),
    onBackground = Color(0xFF839496),
    surface = Color(0xFF002B36),
    onSurface = Color(0xFF839496),
    surfaceVariant = Color(0xFF073642),
    onSurfaceVariant = Color(0xFF93A1A1),
    surfaceContainerLowest = Color(0xFF001F27),
    surfaceContainerLow = Color(0xFF00252E),
    surfaceContainer = Color(0xFF002B36),
    surfaceContainerHigh = Color(0xFF073642),
    surfaceContainerHighest = Color(0xFF0A4452),
    outline = Color(0xFF586E75),
    outlineVariant = Color(0xFF073642),
    error = Color(0xFFDC322F),
    onError = Color(0xFF002B36),
    errorContainer = Color(0xFF3F1618),
    onErrorContainer = Color(0xFFDC322F),
)

val SolarizedLightColors = lightColorScheme(
    primary = Color(0xFF268BD2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEE8D5),
    onPrimaryContainer = Color(0xFF002B36),
    secondary = Color(0xFF2AA198),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0DAC5),
    onSecondaryContainer = Color(0xFF002B36),
    tertiary = Color(0xFF859900),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEEE8D5),
    onTertiaryContainer = Color(0xFF002B36),
    background = Color(0xFFFDF6E3),
    onBackground = Color(0xFF657B83),
    surface = Color(0xFFFDF6E3),
    onSurface = Color(0xFF657B83),
    surfaceVariant = Color(0xFFEEE8D5),
    onSurfaceVariant = Color(0xFF586E75),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF6EB),
    surfaceContainer = Color(0xFFFDF6E3),
    surfaceContainerHigh = Color(0xFFEEE8D5),
    surfaceContainerHighest = Color(0xFFE0DAC5),
    outline = Color(0xFF93A1A1),
    outlineVariant = Color(0xFFEEE8D5),
    error = Color(0xFFDC322F),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/**
 * Resolves the matching [ColorScheme] for a given [AppTheme] and brightness mode.
 */
fun themeColorScheme(theme: AppTheme, dark: Boolean): ColorScheme = when (theme) {
    AppTheme.DEFAULT -> if (dark) DarkColors else LightColors
    AppTheme.CATPPUCCIN -> if (dark) CatppuccinDarkColors else CatppuccinLightColors
    AppTheme.NORD -> if (dark) NordDarkColors else NordLightColors
    AppTheme.DRACULA -> if (dark) DraculaDarkColors else DraculaLightColors
    AppTheme.TOKYO_NIGHT -> if (dark) TokyoNightDarkColors else TokyoNightLightColors
    AppTheme.GRUVBOX -> if (dark) GruvboxDarkColors else GruvboxLightColors
    AppTheme.OLED -> if (dark) OledDarkColors else OledLightColors
    AppTheme.EMERALD -> if (dark) EmeraldDarkColors else EmeraldLightColors
    AppTheme.SUNSET -> if (dark) SunsetDarkColors else SunsetLightColors
    AppTheme.SOLARIZED -> if (dark) SolarizedDarkColors else SolarizedLightColors
}
