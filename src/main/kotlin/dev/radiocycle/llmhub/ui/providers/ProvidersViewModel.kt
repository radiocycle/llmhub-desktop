package dev.radiocycle.llmhub.ui.providers

import dev.radiocycle.llmhub.AppContainer
import dev.radiocycle.llmhub.data.model.BuiltInPresets
import dev.radiocycle.llmhub.data.model.Endpoint
import dev.radiocycle.llmhub.data.model.EndpointHealth
import dev.radiocycle.llmhub.data.model.Provider
import dev.radiocycle.llmhub.data.model.ProviderPreset
import dev.radiocycle.llmhub.net.ClientFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProbeState(
    val running: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

class ProvidersViewModel(private val container: AppContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val providers: StateFlow<List<Provider>> = container.providers.providers
    val health: StateFlow<Map<String, EndpointHealth>> = container.rotation.health

    private val _probe = MutableStateFlow(ProbeState())
    val probe: StateFlow<ProbeState> = _probe.asStateFlow()

    private val _draft = MutableStateFlow<Provider?>(null)
    val draft: StateFlow<Provider?> = _draft.asStateFlow()

    fun startEdit(providerId: String?) {
        _probe.value = ProbeState()
        _draft.value = providerId?.let { container.providers.byId(it) } ?: Provider(
            priority = providers.value.size,
        )
    }

    fun startFromPreset(preset: ProviderPreset) {
        _probe.value = ProbeState()
        _draft.value = preset.toProvider(providers.value.size)
    }

    fun editDraft(transform: (Provider) -> Provider) {
        _draft.value = _draft.value?.let(transform)
    }

    fun addKeysToDraft(newKeys: List<String>, append: Boolean) {
        val draft = _draft.value ?: return
        val existing = if (append) draft.keys else emptyList()
        val combined = (existing + newKeys).distinct()
        _draft.value = draft.copy(apiKey = combined.joinToString("\n"))
    }

    fun addKeysToProvider(providerId: String, newKeys: List<String>, append: Boolean) {
        val provider = container.providers.byId(providerId) ?: return
        val existing = if (append) provider.keys else emptyList()
        val combined = (existing + newKeys).distinct()
        container.providers.upsert(provider.copy(apiKey = combined.joinToString("\n")))
    }

    /**
     * Distributes bulk-scanned keys to basic providers (excluding custom & local).
     */
    fun bulkAddKeys(keysByPreset: Map<String, List<String>>, append: Boolean): Int {
        var count = 0
        keysByPreset.forEach { (presetId, newKeys) ->
            if (presetId == "custom" || presetId == Provider.PRESET_LOCAL || newKeys.isEmpty()) return@forEach
            val preset = BuiltInPresets.byId(presetId) ?: return@forEach

            val existing = container.providers.providers.value.firstOrNull {
                it.presetId == presetId || (it.presetId == null && it.name.equals(preset.name, ignoreCase = true))
            }

            if (existing != null) {
                val combined = if (append) (existing.keys + newKeys).distinct() else newKeys.distinct()
                container.providers.upsert(
                    existing.copy(
                        apiKey = combined.joinToString("\n"),
                        enabled = true,
                    )
                )
            } else {
                val newProvider = preset.toProvider(container.providers.providers.value.size).copy(
                    apiKey = newKeys.distinct().joinToString("\n"),
                    enabled = true,
                )
                container.providers.upsert(newProvider)
            }
            count += newKeys.size
        }
        return count
    }

    fun saveDraft(): Boolean {
        val draft = _draft.value ?: return false
        if (draft.name.isBlank() || draft.baseUrl.isBlank()) return false
        container.providers.upsert(
            draft.copy(
                name = draft.name.trim(),
                baseUrl = draft.baseUrl.trim(),
                apiKey = draft.apiKey.trim(),
                headers = draft.headers.filter { it.name.isNotBlank() },
                models = draft.models.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            )
        )
        _draft.value = null
        return true
    }

    fun discardDraft() {
        _draft.value = null
        _probe.value = ProbeState()
    }

    fun setEnabled(id: String, enabled: Boolean) = container.providers.setEnabled(id, enabled)
    fun delete(id: String) = container.providers.delete(id)
    fun move(id: String, delta: Int) = container.providers.move(id, delta)
    fun clearHealth() = container.rotation.clearHealth()

    fun fetchModels() {
        val provider = _draft.value ?: return
        if (provider.baseUrl.isBlank()) {
            _probe.value = ProbeState(message = "Set a base URL first", isError = true)
            return
        }
        _probe.value = ProbeState(running = true)
        scope.launch {
            val endpoint = Endpoint(provider, 0)
            runCatching { ClientFactory.forMode(provider.apiMode).listModels(endpoint) }.fold(
                onSuccess = { models ->
                    if (models.isEmpty()) {
                        _probe.value = ProbeState(message = "Connected, but no models were returned", isError = false)
                    } else {
                        _draft.value = _draft.value?.let { current ->
                            current.copy(
                                models = models,
                                defaultModel = current.defaultModel.takeIf { it in models } ?: models.first(),
                            )
                        }
                        _probe.value = ProbeState(message = "Found ${models.size} models", isError = false)
                    }
                },
                onFailure = { _probe.value = ProbeState(message = it.message ?: "Connection failed", isError = true) },
            )
        }
    }
}
