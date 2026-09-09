package dev.radiocycle.llmhub

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.radiocycle.llmhub.ui.nav.DesktopAppShell
import dev.radiocycle.llmhub.ui.theme.LlmHubTheme

fun main() = application {
    val container = AppContainer()
    val settings by container.settings.settings.collectAsState()

    val windowState = rememberWindowState(
        width = 1120.dp,
        height = 760.dp,
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "LLMHub",
        icon = painterResource("icon.png"),
    ) {
        LlmHubTheme(themeMode = settings.themeMode) {
            DesktopAppShell(container)
        }
    }
}
