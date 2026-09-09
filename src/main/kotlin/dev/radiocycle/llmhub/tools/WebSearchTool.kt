package dev.radiocycle.llmhub.tools

import dev.radiocycle.llmhub.core.AppJson
import dev.radiocycle.llmhub.data.model.SearchBackend
import dev.radiocycle.llmhub.data.repo.SettingsRepository
import dev.radiocycle.llmhub.net.Http
import dev.radiocycle.llmhub.net.normalizeFirecrawlBaseUrl
import dev.radiocycle.llmhub.net.trimBaseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.net.URLEncoder

data class SearchResult(val title: String, val url: String, val snippet: String)

/** Web search with swappable backends; DuckDuckGo needs no API key and is the default. */
class WebSearchTool(private val settings: SettingsRepository) : AgentTool {

    override val spec = ToolSpec(
        name = "web_search",
        description = "Search the web and return ranked results with titles, URLs and snippets. " +
            "Follow up with web_fetch to read a result in full.",
        parameters = objectSchema(required = listOf("query")) {
            stringProp("query", "The search query.")
            intProp("count", "How many results to return (1-15, default 6).")
        },
    )

    override suspend fun execute(args: JsonObject): ToolOutcome = withContext(Dispatchers.IO) {
        val query = args["query"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (query.isEmpty()) return@withContext ToolOutcome("`query` is required", isError = true)

        val toolSettings = settings.current.tools
        val count = (args["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: toolSettings.maxSearchResults)
            .coerceIn(1, 15)

        runCatching {
            when (toolSettings.searchBackend) {
                SearchBackend.DUCKDUCKGO -> duckDuckGo(query, count)
                SearchBackend.TAVILY -> tavily(query, count, toolSettings.searchApiKey)
                SearchBackend.BRAVE -> brave(query, count, toolSettings.searchApiKey)
                SearchBackend.SEARXNG -> searxng(query, count, toolSettings.searxngUrl)
                SearchBackend.FIRECRAWL -> firecrawl(
                    query,
                    count,
                    toolSettings.firecrawlBaseUrl.ifBlank { "https://api.firecrawl.dev" },
                    toolSettings.firecrawlApiKey.ifBlank { toolSettings.searchApiKey },
                )
            }
        }.fold(
            onSuccess = { results ->
                if (results.isEmpty()) ToolOutcome("No results for \"$query\".")
                else ToolOutcome(format(query, results))
            },
            onFailure = { ToolOutcome("Search failed: ${it.message}", isError = true) },
        )
    }

    private fun format(query: String, results: List<SearchResult>) = buildString {
        appendLine("Search results for \"$query\" (${settings.current.tools.searchBackend.label}):")
        results.forEachIndexed { index, result ->
            appendLine()
            appendLine("${index + 1}. ${result.title}")
            appendLine("   ${result.url}")
            if (result.snippet.isNotBlank()) appendLine("   ${result.snippet}")
        }
    }.trim()

    // --- Backends -------------------------------------------------------------------------

    private fun duckDuckGo(query: String, count: Int): List<SearchResult> {
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/")
            .post(FormBody.Builder().add("q", query).add("kl", "wt-wt").build())
            .header("User-Agent", WebFetchTool.USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val html = Http.withTimeout(30).newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("DuckDuckGo returned HTTP ${response.code}")
            body
        }

        return DDG_RESULT.findAll(html).take(count).map { match ->
            SearchResult(
                title = Html.toText(match.groupValues[2]).trim(),
                url = decodeDdgUrl(match.groupValues[1]),
                snippet = DDG_SNIPPET.find(html, match.range.last)
                    ?.let { Html.toText(it.groupValues[1]).trim() }
                    ?.take(300)
                    .orEmpty(),
            )
        }.filter { it.title.isNotBlank() }.toList()
    }

    private fun decodeDdgUrl(raw: String): String {
        val href = raw.replace("&amp;", "&")
        val encoded = Regex("[?&]uddg=([^&]+)").find(href)?.groupValues?.get(1) ?: return href
        return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(href)
    }

    private fun tavily(query: String, count: Int, apiKey: String): List<SearchResult> {
        require(apiKey.isNotBlank()) { "Tavily API key is not set (Settings → Tools)" }
        val payload = buildJsonObject {
            put("query", query)
            put("max_results", count)
            put("search_depth", "basic")
        }
        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .post(payload.toString().toRequestBody(JSON))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .build()

        return Http.withTimeout(30).newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Tavily returned HTTP ${response.code}: ${body.take(200)}")
            AppJson.parseToJsonElement(body).jsonObject["results"]?.jsonArray?.map { element ->
                val item = element.jsonObject
                SearchResult(
                    title = item["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    url = item["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    snippet = item["content"]?.jsonPrimitive?.contentOrNull.orEmpty().take(400),
                )
            }.orEmpty()
        }
    }

    private fun brave(query: String, count: Int, apiKey: String): List<SearchResult> {
        require(apiKey.isNotBlank()) { "Brave Search API key is not set (Settings → Tools)" }
        val request = Request.Builder()
            .url("https://api.search.brave.com/res/v1/web/search?q=${query.urlEncoded()}&count=$count")
            .header("X-Subscription-Token", apiKey)
            .header("Accept", "application/json")
            .build()

        return Http.withTimeout(30).newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Brave returned HTTP ${response.code}: ${body.take(200)}")
            AppJson.parseToJsonElement(body).jsonObject["web"]?.jsonObject?.get("results")?.jsonArray
                ?.map { element ->
                    val item = element.jsonObject
                    SearchResult(
                        title = item["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        url = item["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        snippet = Html.toText(item["description"]?.jsonPrimitive?.contentOrNull.orEmpty()).take(400),
                    )
                }.orEmpty()
        }
    }

    private fun searxng(query: String, count: Int, instance: String): List<SearchResult> {
        val base = instance.trimBaseUrl().ifBlank { "https://searx.be" }
        val request = Request.Builder()
            .url("$base/search?q=${query.urlEncoded()}&format=json")
            .header("User-Agent", WebFetchTool.USER_AGENT)
            .header("Accept", "application/json")
            .build()

        return Http.withTimeout(30).newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("SearXNG returned HTTP ${response.code} — the instance may not expose the JSON API")
            }
            AppJson.parseToJsonElement(body).jsonObject["results"]?.jsonArray?.take(count)?.map { element ->
                val item = element.jsonObject
                SearchResult(
                    title = item["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    url = item["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    snippet = item["content"]?.jsonPrimitive?.contentOrNull.orEmpty().take(400),
                )
            }.orEmpty()
        }
    }

    private fun firecrawl(query: String, count: Int, baseUrl: String, apiKey: String): List<SearchResult> {
        val rootUrl = baseUrl.normalizeFirecrawlBaseUrl()
        val endpoint = "$rootUrl/v1/search"
        val payload = buildJsonObject {
            put("query", query)
            put("limit", count)
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

        val responseBody = Http.executeWithFallback(request, 30).use { response ->
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

        val data = root["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            val item = element.jsonObject
            val url = item["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = item["title"]?.jsonPrimitive?.contentOrNull
                ?: item["metadata"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
                ?: url
            val snippet = item["description"]?.jsonPrimitive?.contentOrNull
                ?: item["snippet"]?.jsonPrimitive?.contentOrNull
                ?: item["markdown"]?.jsonPrimitive?.contentOrNull?.take(300)
                ?: ""
            SearchResult(title = title.trim(), url = url.trim(), snippet = snippet.trim())
        }
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val DDG_RESULT = Regex(
            "<a[^>]+class=\"[^\"]*result__a[^\"]*\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val DDG_SNIPPET = Regex(
            "class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    }
}
