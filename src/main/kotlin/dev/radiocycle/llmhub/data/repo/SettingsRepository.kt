package dev.radiocycle.llmhub.data.repo

import dev.radiocycle.llmhub.core.XdgPaths
import dev.radiocycle.llmhub.data.model.AppSettings
import dev.radiocycle.llmhub.data.store.JsonFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class SettingsRepository(
    baseDir: File = XdgPaths.configDir,
    scope: CoroutineScope,
) {

    private val store = JsonFileStore(
        baseDir = baseDir,
        fileName = "settings.json",
        serializer = AppSettings.serializer(),
        default = { AppSettings() },
        scope = scope,
    )

    val settings: StateFlow<AppSettings> = store.state
    val current: AppSettings get() = store.value

    fun update(transform: (AppSettings) -> AppSettings) = store.update(transform)
}
