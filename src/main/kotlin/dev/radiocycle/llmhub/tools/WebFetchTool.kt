package dev.radiocycle.llmhub.tools

import dev.radiocycle.llmhub.core.AppJson
import dev.radiocycle.llmhub.data.repo.SettingsRepository
import dev.radiocycle.llmhub.net.Http
import dev.radiocycle.llmhub.net.normalizeFirecrawlBaseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Fetches a URL and returns readable text extracted from the HTML. */
class WebFetchTool(private val settings: SettingsRepository) : AgentTool {

    override val spec = ToolSpec(
        name = "web_fetch",
        description = "Fetch a URL and return its main readable text content. Use it to read pages " +
            "found via web_search, documentation, or any public API that returns text/JSON.",
        parameters = objectSchema(required = listOf("url")) {
            stringProp("url", "Absolute http(s) URL to fetch.")
            intProp("max_chars", "Maximum characters of text to return (default 20000).")
        },
    )

    override suspend fun execute(args: JsonObject): ToolOutcome = withContext(Dispatchers.IO) {
        val url = args["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext ToolOutcome("Invalid url: must start with http:// or https://", isError = true)
        }
        val limit = args["max_chars"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?: settings.current.tools.fetchCharLimit

        val toolSettings = settings.current.tools
        if (toolSettings.scrapeWithFirecrawl) {
            val baseUrl = toolSettings.firecrawlBaseUrl.ifBlank { "https://api.firecrawl.dev" }
            val apiKey = toolSettings.firecrawlApiKey.ifBlank { toolSettings.searchApiKey }
            return@withContext runCatching {
                firecrawlScrape(url, limit, baseUrl, apiKey)
            }.getOrElse { ToolOutcome("Firecrawl scrape failed for $url: ${it.message}", isError = true) }
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,text/plain;q=0.8,*/*;q=0.5")
            .build()

        runCatching {
            Http.executeWithFallback(request, 45).use { response ->
                val contentType = response.header("Content-Type").orEmpty()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use ToolOutcome(
                        "HTTP ${response.code} fetching $url\n${body.take(500)}",
                        isError = true,
                    )
                }
                val text = if (contentType.contains("html", ignoreCase = true)) Html.toText(body) else body
                val truncated = text.length > limit
                ToolOutcome(
                    buildString {
                        appendLine("URL: $url")
                        appendLine("Content-Type: ${contentType.ifBlank { "unknown" }}")
                        appendLine("Length: ${text.length} chars${if (truncated) " (truncated to $limit)" else ""}")
                        appendLine("---")
                        append(text.take(limit))
                    }
                )
            }
        }.getOrElse { ToolOutcome("Failed to fetch $url: ${it.message}", isError = true) }
    }

    private fun firecrawlScrape(url: String, limit: Int, baseUrl: String, apiKey: String): ToolOutcome {
        val rootUrl = baseUrl.normalizeFirecrawlBaseUrl()
        val endpoint = "$rootUrl/v1/scrape"
        val payload = buildJsonObject {
            put("url", url)
            putJsonArray("formats") {
                add("markdown")
            }
        }
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .apply {
                if (apiKey.isNotBlank()) {
                    val key = apiKey.trim()
                    header("Authorization", "Bearer $key")
                    header("x-api-key", key)
                }
            }
            .build()

        val responseBody = Http.executeWithFallback(request, 60).use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Firecrawl returned HTTP ${response.code}: ${body.take(300)}")
            }
            body
        }

        val root = AppJson.parseToJsonElement(responseBody).jsonObject
        if (root["success"]?.jsonPrimitive?.booleanOrNull == false) {
            val err = root["error"]?.jsonPrimitive?.contentOrNull ?: "unknown error"
            error("Firecrawl error: $err")
        }

        val data = root["data"]?.jsonObject ?: error("Firecrawl response missing data object")
        val text = data["markdown"]?.jsonPrimitive?.contentOrNull
            ?: data["content"]?.jsonPrimitive?.contentOrNull
            ?: ""
        val title = data["metadata"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty()
        val truncated = text.length > limit

        return ToolOutcome(
            buildString {
                appendLine("URL: $url")
                if (title.isNotBlank()) appendLine("Title: ${title.trim()}")
                appendLine("Provider: Firecrawl")
                appendLine("Length: ${text.length} chars${if (truncated) " (truncated to $limit)" else ""}")
                appendLine("---")
                append(text.take(limit))
            }
        )
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** Minimal HTML-to-text extraction — no parser dependency, good enough for LLM consumption. */
object Html {
    private val dropBlocks = Regex(
        "<(script|style|noscript|svg|head|nav|footer|form)\\b[^>]*>.*?</\\1>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val breaks = Regex("<(br|/p|/div|/li|/h[1-6]|/tr|/section|/article)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val listItems = Regex("<li\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val headings = Regex("<h[1-6]\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val tags = Regex("<[^>]+>")
    private val blankLines = Regex("\n{3,}")
    private val spaces = Regex("[ \\t\\x0B\\f\\r]{2,}")

    fun toText(html: String): String = html
        .replace(dropBlocks, " ")
        .replace(comments, " ")
        .replace(breaks, "\n")
        .replace(listItems, "\n• ")
        .replace(headings, "\n\n")
        .replace(tags, " ")
        .let(::decodeEntities)
        .replace(spaces, " ")
        .lines().joinToString("\n") { it.trim() }
        .replace(blankLines, "\n\n")
        .trim()

    private val comments = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val numericEntity = Regex("&#(x?)([0-9a-fA-F]+);")

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "mdash" to "—", "ndash" to "–", "hellip" to "…", "laquo" to "«", "raquo" to "»",
        "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”", "copy" to "©",
    )

    private fun decodeEntities(text: String): String {
        var result = numericEntity.replace(text) { match ->
            val radix = if (match.groupValues[1].isEmpty()) 10 else 16
            match.groupValues[2].toIntOrNull(radix)?.takeIf { it in 1..0x10FFFF }
                ?.let { String(Character.toChars(it)) } ?: match.value
        }
        named.forEach { (name, value) -> result = result.replace("&$name;", value) }
        return result
    }
}
