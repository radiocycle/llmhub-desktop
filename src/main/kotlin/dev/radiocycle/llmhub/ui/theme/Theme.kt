package dev.radiocycle.llmhub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.radiocycle.llmhub.data.model.ThemeMode

import dev.radiocycle.llmhub.data.model.AppTheme

@Composable
fun LlmHubTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorTheme: AppTheme = AppTheme.DEFAULT,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = themeColorScheme(colorTheme, dark)

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ExpressiveShapes,
        content = content,
    )
}
