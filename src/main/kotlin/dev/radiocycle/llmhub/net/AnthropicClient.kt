package dev.radiocycle.llmhub.net

import dev.radiocycle.llmhub.core.AppJson
import dev.radiocycle.llmhub.data.model.ChatMessage
import dev.radiocycle.llmhub.data.model.Endpoint
import dev.radiocycle.llmhub.data.model.Provider
import dev.radiocycle.llmhub.data.model.ReasoningEffort
import dev.radiocycle.llmhub.data.model.Role
import dev.radiocycle.llmhub.data.model.TokenUsage
import dev.radiocycle.llmhub.data.model.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.coroutineContext

/** Anthropic Messages API dialect. */
class AnthropicClient : LlmClient {

    override fun stream(endpoint: Endpoint, request: ChatRequest): Flow<StreamEvent> = flow {
        val provider = endpoint.provider
        val url = "${provider.baseUrl.trimBaseUrl()}/v1/messages"
        val payload = buildPayload(provider, request)
        val call = Http.withTimeout(provider.timeoutSeconds).newCall(
            Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(OpenAiClient.JSON_MEDIA))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("anthropic-version", ANTHROPIC_VERSION)
                .apply { if (endpoint.apiKey.isNotBlank()) header("x-api-key", endpoint.apiKey) }
                .applyCustomHeaders(provider.headers)
                .build()
        )

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw httpFailure(response, response.body?.string())

                val blocks = mutableMapOf<Int, BlockAccumulator>()
                val toolCalls = mutableListOf<ToolCall>()
                var stopReason: String? = null
                var usage = TokenUsage()

                response.readSse { event ->
                    coroutineContext.ensureActive()
                    if (event.data.isBlank()) return@readSse true
                    val root = runCatching { AppJson.parseToJsonElement(event.data).jsonObject }.getOrNull()
                        ?: return@readSse true

                    when (root["type"]?.jsonPrimitive?.contentOrNull ?: event.name) {
                        "error" -> {
                            val error = root["error"]?.jsonObject
                            throw LlmException(
                                error?.get("message")?.jsonPrimitive?.contentOrNull ?: "provider error",
                                kind = when (error?.get("type")?.jsonPrimitive?.contentOrNull) {
                                    "rate_limit_error" -> LlmException.Kind.RATE_LIMIT
                                    "overloaded_error", "api_error" -> LlmException.Kind.SERVER
                                    "authentication_error", "permission_error" -> LlmException.Kind.AUTH
                                    else -> LlmException.Kind.BAD_REQUEST
                                },
                            )
                        }

                        "message_start" -> {
                            root["message"]?.jsonObject?.get("usage")?.jsonObject?.let { u ->
                                usage = usage.copy(inputTokens = u["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0)
                            }
                        }

                        "content_block_start" -> {
                            val index = root["index"]?.jsonPrimitive?.intOrNull ?: 0
                            val block = root["content_block"]?.jsonObject
                            blocks[index] = BlockAccumulator(
                                type = block?.get("type")?.jsonPrimitive?.contentOrNull.orEmpty(),
                                id = block?.get("id")?.jsonPrimitive?.contentOrNull,
                                name = block?.get("name")?.jsonPrimitive?.contentOrNull,
                            )
                        }

                        "content_block_delta" -> {
                            val index = root["index"]?.jsonPrimitive?.intOrNull ?: 0
                            val delta = root["delta"]?.jsonObject ?: return@readSse true
                            when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                                "text_delta" -> delta["text"]?.jsonPrimitive?.contentOrNull
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { emit(StreamEvent.TextDelta(it)) }

                                "thinking_delta" -> delta["thinking"]?.jsonPrimitive?.contentOrNull
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { emit(StreamEvent.ReasoningDelta(it)) }

                                "input_json_delta" -> delta["partial_json"]?.jsonPrimitive?.contentOrNull
                                    ?.let { blocks[index]?.json?.append(it) }
                            }
                        }

                        "content_block_stop" -> {
                            val index = root["index"]?.jsonPrimitive?.intOrNull ?: 0
                            blocks.remove(index)?.takeIf { it.type == "tool_use" }?.let { block ->
                                toolCalls += ToolCall(
                                    id = block.id ?: "toolu_${System.nanoTime()}",
                                    name = block.name.orEmpty(),
                                    argumentsJson = block.json.toString().ifBlank { "{}" },
                                )
                            }
                        }

                        "message_delta" -> {
                            root["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull
                                ?.let { stopReason = it }
                            root["usage"]?.jsonObject?.get("output_tokens")?.jsonPrimitive?.intOrNull
                                ?.let { usage = usage.copy(outputTokens = it) }
                        }

                        "message_stop" -> return@readSse false
                    }
                    true
                }

                if (toolCalls.isNotEmpty()) emit(StreamEvent.ToolCalls(toolCalls.filter { it.name.isNotBlank() }))
                emit(StreamEvent.Completed(stopReason, usage))
            }
        } catch (t: Throwable) {
            coroutineContext.ensureActive()
            throw t.toLlmException(provider.name)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(endpoint: Endpoint): List<String> = withContext(Dispatchers.IO) {
        val provider = endpoint.provider
        val request = Request.Builder()
            .url("${provider.baseUrl.trimBaseUrl()}/v1/models?limit=200")
            .header("anthropic-version", ANTHROPIC_VERSION)
            .apply { if (endpoint.apiKey.isNotBlank()) header("x-api-key", endpoint.apiKey) }
            .applyCustomHeaders(provider.headers)
            .build()
        try {
            Http.withTimeout(30).newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw httpFailure(response, body)
                AppJson.parseToJsonElement(body).jsonObject["data"]?.jsonArray
                    ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                    .orEmpty()
            }
        } catch (t: Throwable) {
            throw t.toLlmException(provider.name)
        }
    }

    private fun buildPayload(provider: Provider, request: ChatRequest): JsonObject = buildJsonObject {
        put("model", request.model)
        put("max_tokens", request.maxTokens)
        put("stream", true)
        request.system?.takeIf { it.isNotBlank() }?.let { put("system", it) }
        put("messages", buildMessages(request))

        val customBudget = provider.customEffort.toIntOrNull()
        if (provider.effortParameter.isNotBlank()) {
            val customVal = provider.effectiveEffortValue
            if (!customVal.isNullOrBlank()) {
                val intVal = customVal.toIntOrNull()
                if (intVal != null) put(provider.effortParameter.trim(), intVal)
                else put(provider.effortParameter.trim(), customVal)
            }
            put("temperature", request.temperature.coerceIn(0f, 1f))
        } else if (customBudget != null) {
            if (customBudget <= 0) {
                putJsonObject("thinking") { put("type", "disabled") }
            } else {
                putJsonObject("thinking") {
                    put("type", "enabled")
                    put("budget_tokens", customBudget)
                }
                put("temperature", 1.0f)
            }
        } else if (provider.reasoningEffort != ReasoningEffort.DEFAULT) {
            when (provider.reasoningEffort) {
                ReasoningEffort.NONE -> putJsonObject("thinking") { put("type", "disabled") }
                ReasoningEffort.LOW -> {
                    putJsonObject("thinking") {
                        put("type", "enabled")
                        put("budget_tokens", 1024)
                    }
                    put("temperature", 1.0f)
                }
                ReasoningEffort.MEDIUM -> {
                    putJsonObject("thinking") {
                        put("type", "enabled")
                        put("budget_tokens", 2048)
                    }
                    put("temperature", 1.0f)
                }
                ReasoningEffort.HIGH -> {
                    putJsonObject("thinking") {
                        put("type", "enabled")
                        put("budget_tokens", 4096)
                    }
                    put("temperature", 1.0f)
                }
                else -> put("temperature", request.temperature.coerceIn(0f, 1f))
            }
        } else {
            put("temperature", request.temperature.coerceIn(0f, 1f))
        }
        if (request.tools.isNotEmpty()) {
            putJsonArray("tools") {
                request.tools.forEach { tool ->
                    addJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("input_schema", tool.parameters)
                    }
                }
            }
        }
    }

    /**
     * Anthropic requires strictly alternating user/assistant turns, so neighbouring messages of
     * the same role are merged into one multi-block turn.
     */
    private fun buildMessages(request: ChatRequest): JsonArray {
        data class Turn(val role: String, val blocks: MutableList<JsonObject>)

        val turns = mutableListOf<Turn>()
        fun push(role: String, blocks: List<JsonObject>) {
            if (blocks.isEmpty()) return
            val last = turns.lastOrNull()
            if (last != null && last.role == role) last.blocks += blocks
            else turns += Turn(role, blocks.toMutableList())
        }

        request.messages.forEach { message ->
            when (message.role) {
                Role.SYSTEM -> Unit // handled as the top-level `system` field
                Role.USER -> push("user", textBlocks(message.content))
                Role.ASSISTANT -> push("assistant", assistantBlocks(message))
                Role.TOOL -> push("user", message.toolResults.map { result ->
                    buildJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", result.callId)
                        put("content", result.content.ifBlank { "(empty)" })
                        if (result.isError) put("is_error", true)
                    }
                })
            }
        }

        // Native assistant prefill: a trailing assistant turn makes the model continue it.
        request.prefill?.trimEnd()?.takeIf { it.isNotEmpty() }?.let { push("assistant", textBlocks(it)) }

        if (turns.firstOrNull()?.role == "assistant") {
            turns.add(0, Turn("user", mutableListOf(buildJsonObject {
                put("type", "text"); put("text", "(continue)")
            })))
        }

        return buildJsonArray {
            turns.forEach { turn ->
                addJsonObject {
                    put("role", turn.role)
                    putJsonArray("content") { turn.blocks.forEach { add(it) } }
                }
            }
        }
    }

    private fun textBlocks(text: String): List<JsonObject> =
        if (text.isBlank()) emptyList() else listOf(buildJsonObject {
            put("type", "text"); put("text", text)
        })

    private fun assistantBlocks(message: ChatMessage): List<JsonObject> = buildList {
        addAll(textBlocks(message.content))
        message.toolCalls.forEach { call ->
            add(buildJsonObject {
                put("type", "tool_use")
                put("id", call.id)
                put("name", call.name)
                put("input", runCatching { AppJson.parseToJsonElement(call.argumentsJson).jsonObject }
                    .getOrElse { JsonObject(emptyMap()) })
            })
        }
    }

    private class BlockAccumulator(val type: String, val id: String?, val name: String?) {
        val json = StringBuilder()
    }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
