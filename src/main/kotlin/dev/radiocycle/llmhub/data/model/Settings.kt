package dev.radiocycle.llmhub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SearchBackend {
    @SerialName("duckduckgo") DUCKDUCKGO,
    @SerialName("tavily") TAVILY,
    @SerialName("brave") BRAVE,
    @SerialName("searxng") SEARXNG,
    @SerialName("firecrawl") FIRECRAWL;

    val label: String
        get() = when (this) {
            DUCKDUCKGO -> "DuckDuckGo"
            TAVILY -> "Tavily"
            BRAVE -> "Brave Search"
            SEARXNG -> "SearXNG"
            FIRECRAWL -> "Firecrawl"
        }

    val needsKey: Boolean get() = this == TAVILY || this == BRAVE
}

@Serializable
enum class ThemeMode {
    @SerialName("system") SYSTEM,
    @SerialName("light") LIGHT,
    @SerialName("dark") DARK;

    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

@Serializable
data class RotationSettings(
    val strategy: RotationStrategy = RotationStrategy.FAILOVER,
    /** Total endpoints tried for a single turn before giving up. */
    val maxAttempts: Int = 4,
    /** Base cooldown applied to a failing endpoint; doubles per consecutive failure. */
    val cooldownSeconds: Int = 30,
    val maxCooldownSeconds: Int = 600,
    /**
     * When a stream dies after tokens were already shown, hand the partial text to the next
     * provider as a prefill and continue instead of restarting the answer.
     */
    val midStreamHandoff: Boolean = true,
    /**
     * Credential failures — 401, 402, 403 and 429 — walk the whole key pool silently and only
     * surface once every key of every provider has been tried.
     */
    val rotateOnKeyError: Boolean = true,
    /** Retry on 5xx / network errors. */
    val rotateOnServerError: Boolean = true,
    /** Also rotate when the model itself is missing on that endpoint (404 model_not_found). */
    val rotateOnModelMissing: Boolean = true,
)

@Serializable
data class ToolSettings(
    val webSearchEnabled: Boolean = true,
    val webFetchEnabled: Boolean = true,
    val execJsEnabled: Boolean = true,
    val searchBackend: SearchBackend = SearchBackend.DUCKDUCKGO,
    val searchApiKey: String = "",
    val searxngUrl: String = "https://searx.be",
    val firecrawlBaseUrl: String = "https://api.firecrawl.dev",
    val firecrawlApiKey: String = "",
    val scrapeWithFirecrawl: Boolean = false,
    val maxSearchResults: Int = 6,
    val fetchCharLimit: Int = 20000,
    val jsTimeoutMs: Long = 5000,
    val maxToolIterations: Int = 8,

    // --- Filesystem + shell -----------------------------------------------------------------
    /** Absolute path of the agent's working directory. Blank means the app-private default. */
    val workspacePath: String = "",
    /** Offer read_file / write_file / edit_file / delete_file / list_files. */
    val fileToolsEnabled: Boolean = true,
    /**
     * Confine file tools and the shell's own path resolution to the workspace. When on, an absolute
     * path or a `..` that climbs out of the workspace is refused. Turn off for a full-device agent.
     */
    val restrictToWorkspace: Boolean = true,
    /** Offer the shell tool. Off by default — it runs real commands on the device. */
    val shellEnabled: Boolean = false,
    /** Run shell commands through `su` / root when access is granted. */
    val shellUseRoot: Boolean = false,
    val shellTimeoutMs: Long = 30000,
    val fileReadCharLimit: Int = 60000,
)

@Serializable
data class AppSettings(
    val systemPrompt: String = "",
    val temperature: Float = 1.0f,
    val maxTokens: Int = 4096,
    val streamResponses: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Render replies with the full Markdown + LaTeX engine instead of the lightweight one. */
    val richRendering: Boolean = true,
    val rotation: RotationSettings = RotationSettings(),
    val tools: ToolSettings = ToolSettings(),
)
