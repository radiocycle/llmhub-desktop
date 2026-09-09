package dev.radiocycle.llmhub.data.repo

import dev.radiocycle.llmhub.core.XdgPaths
import dev.radiocycle.llmhub.data.model.Provider
import dev.radiocycle.llmhub.data.store.JsonFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

class ProviderRepository(
    baseDir: File = XdgPaths.configDir,
    scope: CoroutineScope,
) {

    private val store = JsonFileStore(
        baseDir = baseDir,
        fileName = "providers.json",
        serializer = ListSerializer(Provider.serializer()),
        default = { emptyList() },
        scope = scope,
    )

    val providers: StateFlow<List<Provider>> = store.state

    /** Rotation order: enabled + configured first, then by priority. */
    fun rotationPool(): List<Provider> = store.value
        .filter { it.enabled && it.baseUrl.isNotBlank() }
        .sortedWith(compareBy({ it.priority }, { it.name }))

    fun byId(id: String?): Provider? = id?.let { pid -> store.value.firstOrNull { it.id == pid } }

    fun add(provider: Provider) = store.update { list ->
        list + provider.copy(priority = provider.priority.takeIf { it != 0 } ?: list.size)
    }

    fun upsert(provider: Provider) = store.update { list ->
        if (list.any { it.id == provider.id }) list.map { if (it.id == provider.id) provider else it }
        else list + provider
    }

    fun delete(id: String) = store.update { list -> list.filterNot { it.id == id }.reindex() }

    fun setEnabled(id: String, enabled: Boolean) = store.update { list ->
        list.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    fun move(id: String, delta: Int) = store.update { list ->
        val ordered = list.sortedWith(compareBy({ it.priority }, { it.name })).toMutableList()
        val index = ordered.indexOfFirst { it.id == id }
        val target = index + delta
        if (index < 0 || target !in ordered.indices) return@update list
        ordered.add(target, ordered.removeAt(index))
        ordered.reindex()
    }

    fun replaceAll(list: List<Provider>) = store.update { list.reindex() }

    private fun List<Provider>.reindex(): List<Provider> =
        mapIndexed { index, provider -> provider.copy(priority = index) }
}
