package dev.radiocycle.llmhub.data.model

import dev.radiocycle.llmhub.core.AppJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** Wire protocol a provider speaks. Everything else (auth header, path, body) follows from it. */
@Serializable
enum class ApiMode {
    @SerialName("openai") OPENAI,
    @SerialName("anthropic") ANTHROPIC,
    @SerialName("google") GOOGLE;

    val label: String
        get() = when (this) {
            OPENAI -> "OpenAI"
            ANTHROPIC -> "Anthropic"
            GOOGLE -> "Google"
        }
}

/** Dialect / endpoint flavor for OpenAI (+compatible) endpoints. */
@Serializable
enum class OpenAiMode {
    @SerialName("wire") WIRE,
    @SerialName("responses") RESPONSES;

    val label: String
        get() = when (this) {
            WIRE -> "Wire (/chat/completions)"
            RESPONSES -> "Responses (/responses)"
        }

    val shortLabel: String
        get() = when (this) {
            WIRE -> "Wire"
            RESPONSES -> "Responses"
        }
}

/** Reasoning effort for reasoning models across providers. */
@Serializable
enum class ReasoningEffort {
    @SerialName("default") DEFAULT,
    @SerialName("none") NONE,
    @SerialName("low") LOW,
    @SerialName("medium") MEDIUM,
    @SerialName("high") HIGH;

    val label: String
        get() = when (this) {
            DEFAULT -> "Default"
            NONE -> "None"
            LOW -> "Low"
            MEDIUM -> "Medium"
            HIGH -> "High"
        }

    val value: String?
        get() = when (this) {
            DEFAULT -> null
            NONE -> "none"
            LOW -> "low"
            MEDIUM -> "medium"
            HIGH -> "high"
        }
}

@Serializable
data class HeaderEntry(
    val name: String = "",
    val value: String = "",
)

/**
 * A single callable endpoint. Built-in presets and user-defined custom providers are the same
 * type — a preset merely pre-fills the fields.
 */
@Serializable
data class Provider(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val apiMode: ApiMode = ApiMode.OPENAI,
    val openAiMode: OpenAiMode = OpenAiMode.WIRE,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.DEFAULT,
    val effortParameter: String = "",
    val customEffort: String = "",
    /** Base URL without the endpoint path, e.g. `https://api.openai.com/v1`. */
    val baseUrl: String = "",
    /** One or more API keys, separated by commas, semicolons or newlines. Rotated in order. */
    val apiKey: String = "",
    val headers: List<HeaderEntry> = emptyList(),
    val models: List<String> = emptyList(),
    val defaultModel: String = "",
    val enabled: Boolean = true,
    /** Lower value wins in FAILOVER order; also the display order. */
    val priority: Int = 0,
    /** Relative pick chance for WEIGHTED rotation. */
    val weight: Int = 1,
    val timeoutSeconds: Int = 120,
    val supportsTools: Boolean = true,
    val presetId: String? = null,
    val notes: String = "",
) {
    /** The key pool, in rotation order. Empty for endpoints that need no credential. */
    val keys: List<String>
        get() = KeyParser.parse(apiKey)

    /**
     * How many distinct credentials this provider can be tried with. A provider with no key still
     * has one slot, so keyless local endpoints stay reachable.
     */
    val keySlotCount: Int get() = keys.size.coerceAtLeast(1)

    fun keyAt(index: Int): String = keys.getOrElse(index) { "" }

    fun modelOrDefault(requested: String?): String =
        requested?.takeIf { it.isNotBlank() && (models.isEmpty() || it in models) }
            ?: defaultModel.takeIf { it.isNotBlank() }
            ?: models.firstOrNull()
            ?: ""

    val effectiveEffortValue: String?
        get() = customEffort.trim().takeIf { it.isNotEmpty() } ?: reasoningEffort.value

    fun effectiveEffortParam(defaultName: String = "reasoning_effort"): String =
        effortParameter.trim().ifEmpty { defaultName }

    companion object {
        const val PRESET_LOCAL = "local"
    }
}

/** How the engine picks the next endpoint when several are eligible. */
@Serializable
enum class RotationStrategy {
    @SerialName("failover") FAILOVER,
    @SerialName("round_robin") ROUND_ROBIN,
    @SerialName("weighted") WEIGHTED,
    @SerialName("least_used") LEAST_USED;

    val label: String
        get() = when (this) {
            FAILOVER -> "Failover"
            ROUND_ROBIN -> "Round-robin"
            WEIGHTED -> "Weighted"
            LEAST_USED -> "Least used"
        }

    val description: String
        get() = when (this) {
            FAILOVER -> "Always start from the highest-priority provider, fall through on error"
            ROUND_ROBIN -> "Spread requests evenly across every healthy provider"
            WEIGHTED -> "Pick randomly, proportional to each provider's weight"
            LEAST_USED -> "Prefer the provider that has been idle the longest"
        }
}

/** Runtime health of one provider+key pair. Not persisted — it is rebuilt every launch. */
data class EndpointHealth(
    val endpointId: String,
    val successes: Int = 0,
    val failures: Int = 0,
    val consecutiveFailures: Int = 0,
    val cooldownUntil: Long = 0L,
    val lastUsedAt: Long = 0L,
    val lastLatencyMs: Long = 0L,
    val lastError: String? = null,
) {
    fun isCoolingDown(now: Long = System.currentTimeMillis()) = now < cooldownUntil
}

/**
 * One provider paired with one of its API keys — the actual unit the rotation engine schedules,
 * tracks health for, and hands to a client.
 */
data class Endpoint(
    val provider: Provider,
    val keyIndex: Int,
) {
    val apiKey: String get() = provider.keyAt(keyIndex)

    /** Stable identity for health tracking. */
    val id: String get() = "${provider.id}#$keyIndex"

    /** `null` when the provider has a single credential, otherwise a 1-based label for the UI. */
    val keyLabel: String? get() = if (provider.keySlotCount > 1) "key ${keyIndex + 1}" else null

    val maskedKey: String
        get() = apiKey.let { key ->
            when {
                key.isEmpty() -> "(no key)"
                key.length <= 10 -> "…" + key.takeLast(4)
                else -> key.take(4) + "…" + key.takeLast(4)
            }
        }
}

/**
 * Utility for parsing API keys from file contents or user input.
 * Supports:
 * - One key per line
 * - Comma-separated and semicolon-separated keys
 * - JSON arrays of strings: `["key1", "key2"]`
 * - Key-value / env format: `API_KEY=key1`
 * - Strips whitespace and quotes (", ', `)
 * - Ignores comments (# or //) and empty entries
 * - Deduplicates while preserving order
 */
object KeyParser {

    fun parse(content: String): List<String> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. JSON array of strings: ["key1", "key2", ...]
        if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
            val jsonKeys = runCatching {
                AppJson.parseToJsonElement(trimmed).jsonArray.mapNotNull { element ->
                    runCatching {
                        element.jsonPrimitive.content.trim().trim('"', '\'', '`')
                    }.getOrNull()?.takeIf { it.isNotEmpty() }
                }
            }.getOrNull()
            if (!jsonKeys.isNullOrEmpty()) {
                return jsonKeys.distinct()
            }
        }

        // 2. Line-by-line / delimiter-based
        return trimmed.lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() && !line.startsWith('#') && !line.startsWith("//")
            }
            .map { line ->
                // Support env-style lines like OPENAI_API_KEY=sk-...
                if (line.contains('=') && !line.startsWith("sk-")) {
                    line.substringAfter('=').trim()
                } else {
                    line
                }
            }
            .flatMap { line ->
                line.split(',', ';').asSequence()
            }
            .map { key ->
                key.trim().trim('"', '\'', '`')
            }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }
}
