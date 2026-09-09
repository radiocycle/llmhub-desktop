package dev.radiocycle.llmhub.net

import dev.radiocycle.llmhub.core.AppJson
import dev.radiocycle.llmhub.data.model.ChatMessage
import dev.radiocycle.llmhub.data.model.Endpoint
import dev.radiocycle.llmhub.data.model.OpenAiMode
import dev.radiocycle.llmhub.data.model.Provider
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.coroutineContext

/** Chat Completions and Responses dialect — covers OpenAI, OpenRouter, Groq, DeepSeek, Ollama and friends. */
class OpenAiClient : LlmClient {

    override fun stream(endpoint: Endpoint, request: ChatRequest): Flow<StreamEvent> = flow {
        val provider = endpoint.provider
        val isResponses = provider.openAiMode == OpenAiMode.RESPONSES
        val url = if (isResponses) {
            "${provider.baseUrl.trimBaseUrl()}/responses"
        } else {
            "${provider.baseUrl.trimBaseUrl()}/chat/completions"
        }
        val payload = if (isResponses) {
            buildResponsesPayload(provider, request)
        } else {
            buildWirePayload(provider, request, stream = true)
        }
        val http = Http.withTimeout(provider.timeoutSeconds)
        val call = http.newCall(
            Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .apply { if (endpoint.apiKey.isNotBlank()) header("Authorization", "Bearer ${endpoint.apiKey}") }
                .applyCustomHeaders(provider.headers)
                .build()
        )

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw httpFailure(response, response.body?.string())

                val toolAcc = sortedMapOf<Int, ToolAccumulator>()
                var finish: String? = null
                var usage: TokenUsage? = null

                response.readSse { event ->
                    coroutineContext.ensureActive()
                    val data = event.data
                    if (data.isBlank()) return@readSse true
                    if (data.trim() == "[DONE]") return@readSse false

                    val root = runCatching { AppJson.parseToJsonElement(data).jsonObject }.getOrNull()
                        ?: return@readSse true

                    root["error"]?.jsonObject?.let { err ->
                        throw LlmException(
                            err["message"]?.jsonPrimitive?.contentOrNull ?: "provider error",
                            kind = LlmException.Kind.SERVER,
                        )
                    }

                    val eventType = event.name ?: root["type"]?.jsonPrimitive?.contentOrNull

                    // 1. Check for Responses API events
                    when (eventType) {
                        "response.output_text.delta" -> {
                            val delta = root["delta"]?.jsonPrimitive?.contentOrNull
                                ?: root["text"]?.jsonPrimitive?.contentOrNull
                            if (!delta.isNullOrEmpty()) emit(StreamEvent.TextDelta(delta))
                        }
                        "response.reasoning.delta", "response.reasoning_text.delta" -> {
                            val delta = root["delta"]?.jsonPrimitive?.contentOrNull
                                ?: root["reasoning"]?.jsonPrimitive?.contentOrNull
                            if (!delta.isNullOrEmpty()) emit(StreamEvent.ReasoningDelta(delta))
                        }
                        "response.output_item.added" -> {
                            val item = root["item"]?.jsonObject
                            if (item != null && item["type"]?.jsonPrimitive?.contentOrNull == "function_call") {
                                val callId = item["call_id"]?.jsonPrimitive?.contentOrNull
                                    ?: item["id"]?.jsonPrimitive?.contentOrNull
                                val name = item["name"]?.jsonPrimitive?.contentOrNull
                                val index = root["output_index"]?.jsonPrimitive?.intOrNull ?: toolAcc.size
                                val acc = toolAcc.getOrPut(index) { ToolAccumulator() }
                                if (callId != null) acc.id = callId
                                if (name != null) acc.name = name
                            }
                        }
                        "response.function_call_arguments.delta" -> {
                            val index = root["output_index"]?.jsonPrimitive?.intOrNull ?: 0
                            val acc = toolAcc.getOrPut(index) { ToolAccumulator() }
                            root["call_id"]?.jsonPrimitive?.contentOrNull?.let { acc.id = it }
                            root["name"]?.jsonPrimitive?.contentOrNull?.let { acc.name = it }
                            root["delta"]?.jsonPrimitive?.contentOrNull?.let { acc.arguments.append(it) }
                        }
                        "response.completed" -> {
                            val resp = root["response"]?.jsonObject
                            resp?.get("status")?.jsonPrimitive?.contentOrNull?.let { finish = it }
                            val u = resp?.get("usage")?.jsonObject ?: root["usage"]?.jsonObject
                            if (u != null) {
                                usage = TokenUsage(
                                    inputTokens = u["input_tokens"]?.jsonPrimitive?.intOrNull
                                        ?: u["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                                    outputTokens = u["output_tokens"]?.jsonPrimitive?.intOrNull
                                        ?: u["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                                )
                            }
                        }
                    }

                    // 2. Check standard Chat Completions / Wire fields and fallbacks
                    root["usage"]?.jsonObject?.let { u ->
                        usage = TokenUsage(
                            inputTokens = u["prompt_tokens"]?.jsonPrimitive?.intOrNull
                                ?: u["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                            outputTokens = u["completion_tokens"]?.jsonPrimitive?.intOrNull
                                ?: u["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                        )
                    }

                    val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    if (choice != null) {
                        choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let { finish = it }
                        val delta = choice["delta"]?.jsonObject
                        if (delta != null) {
                            delta["content"]?.jsonPrimitive?.contentOrNull
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { emit(StreamEvent.TextDelta(it)) }

                            // DeepSeek-style separate reasoning channel.
                            (delta["reasoning_content"] ?: delta["reasoning"])?.jsonPrimitive?.contentOrNull
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { emit(StreamEvent.ReasoningDelta(it)) }

                            delta["tool_calls"]?.jsonArray?.forEach { element ->
                                val call = element.jsonObject
                                val index = call["index"]?.jsonPrimitive?.intOrNull ?: 0
                                val acc = toolAcc.getOrPut(index) { ToolAccumulator() }
                                call["id"]?.jsonPrimitive?.contentOrNull?.let { acc.id = it }
                                call["function"]?.jsonObject?.let { fn ->
                                    fn["name"]?.jsonPrimitive?.contentOrNull?.let { acc.name = it }
                                    fn["arguments"]?.jsonPrimitive?.contentOrNull?.let { acc.arguments.append(it) }
                                }
                            }
                        }
                    }
                    true
                }

                if (toolAcc.isNotEmpty()) emit(StreamEvent.ToolCalls(toolAcc.values.mapNotNull { it.build() }))
                emit(StreamEvent.Completed(finish, usage))
            }
        } catch (t: Throwable) {
            coroutineContext.ensureActive()
            throw t.toLlmException(provider.name)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(endpoint: Endpoint): List<String> = withContext(Dispatchers.IO) {
        val provider = endpoint.provider
        val request = Request.Builder()
            .url("${provider.baseUrl.trimBaseUrl()}/models")
            .apply { if (endpoint.apiKey.isNotBlank()) header("Authorization", "Bearer ${endpoint.apiKey}") }
            .applyCustomHeaders(provider.headers)
            .build()
        try {
            Http.withTimeout(30).newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw httpFailure(response, body)
                AppJson.parseToJsonElement(body).jsonObject["data"]?.jsonArray
                    ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                    ?.sorted()
                    .orEmpty()
            }
        } catch (t: Throwable) {
            throw t.toLlmException(provider.name)
        }
    }

    private fun buildWirePayload(provider: Provider, request: ChatRequest, stream: Boolean): JsonObject = buildJsonObject {
        put("model", request.model)
        put("stream", stream)
        if (stream) putJsonObject("stream_options") { put("include_usage", true) }
        put("temperature", request.temperature)
        put("max_tokens", request.maxTokens)

        val effort = provider.effectiveEffortValue
        if (!effort.isNullOrBlank()) {
            val paramName = provider.effectiveEffortParam("reasoning_effort")
            val intVal = effort.toIntOrNull()
            val boolVal = effort.toBooleanStrictOrNull()
            when {
                intVal != null -> put(paramName, intVal)
                boolVal != null -> put(paramName, boolVal)
                else -> put(paramName, effort)
            }
        }

        put("messages", buildMessages(request))
        if (request.tools.isNotEmpty()) {
            putJsonArray("tools") {
                request.tools.forEach { tool ->
                    add(buildJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.parameters)
                        }
                    })
                }
            }
            put("tool_choice", "auto")
        }
    }

    private fun buildResponsesPayload(provider: Provider, request: ChatRequest): JsonObject = buildJsonObject {
        put("model", request.model)
        put("stream", true)
        put("store", false)
        put("temperature", request.temperature)
        put("max_output_tokens", request.maxTokens)

        val effort = provider.effectiveEffortValue
        if (!effort.isNullOrBlank()) {
            val paramName = provider.effectiveEffortParam("effort")
            putJsonObject("reasoning") {
                val intVal = effort.toIntOrNull()
                when {
                    intVal != null -> put(paramName, intVal)
                    else -> put(paramName, effort)
                }
            }
        }

        val system = buildString {
            request.system?.takeIf { it.isNotBlank() }?.let { append(it) }
            if (request.prefill != null) {
                if (isNotEmpty()) append("\n\n")
                append(CONTINUATION_HINT)
            }
        }
        if (system.isNotBlank()) {
            put("instructions", system)
        }

        putJsonArray("input") {
            request.messages.forEach { message ->
                when (message.role) {
                    Role.SYSTEM -> add(buildJsonObject {
                        put("role", "system"); put("content", message.content)
                    })
                    Role.USER -> add(buildJsonObject {
                        put("role", "user"); put("content", message.content)
                    })
                    Role.ASSISTANT -> add(assistantMessage(message))
                    Role.TOOL -> message.toolResults.forEach { result ->
                        add(buildJsonObject {
                            put("type", "function_call_output")
                            put("call_id", result.callId)
                            put("output", result.content)
                        })
                    }
                }
            }
            request.prefill?.takeIf { it.isNotBlank() }?.let { partial ->
                add(buildJsonObject {
                    put("role", "assistant")
                    put("content", partial)
                })
            }
        }

        if (request.tools.isNotEmpty()) {
            putJsonArray("tools") {
                request.tools.forEach { tool ->
                    add(buildJsonObject {
                        put("type", "function")
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.parameters)
                    })
                }
            }
        }
    }

    private fun buildMessages(request: ChatRequest): JsonArray = buildJsonArray {
        val system = buildString {
            request.system?.takeIf { it.isNotBlank() }?.let { append(it) }
            if (request.prefill != null) {
                if (isNotEmpty()) append("\n\n")
                append(CONTINUATION_HINT)
            }
        }
        if (system.isNotBlank()) {
            add(buildJsonObject {
                put("role", "system")
                put("content", system)
            })
        }

        request.messages.forEach { message ->
            when (message.role) {
                Role.SYSTEM -> add(buildJsonObject {
                    put("role", "system"); put("content", message.content)
                })
                Role.USER -> add(buildJsonObject {
                    put("role", "user"); put("content", message.content)
                })
                Role.ASSISTANT -> add(assistantMessage(message))
                Role.TOOL -> message.toolResults.forEach { result ->
                    add(buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", result.callId)
                        put("content", result.content)
                    })
                }
            }
        }

        request.prefill?.takeIf { it.isNotBlank() }?.let { partial ->
            add(buildJsonObject {
                put("role", "assistant")
                put("content", partial)
            })
        }
    }

    private fun assistantMessage(message: ChatMessage): JsonObject = buildJsonObject {
        put("role", "assistant")
        put("content", message.content)
        if (message.toolCalls.isNotEmpty()) {
            putJsonArray("tool_calls") {
                message.toolCalls.forEach { call ->
                    add(buildJsonObject {
                        put("id", call.id)
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", call.name)
                            put("arguments", call.argumentsJson)
                        }
                    })
                }
            }
        }
    }

    private class ToolAccumulator {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()

        fun build(): ToolCall? {
            val toolName = name ?: return null
            return ToolCall(
                id = id ?: "call_${toolName}_${System.nanoTime()}",
                name = toolName,
                argumentsJson = arguments.toString().ifBlank { "{}" },
            )
        }
    }

    companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val CONTINUATION_HINT =
            "CONTINUATION MODE: the final assistant message is an unfinished reply that was cut off " +
                "mid-generation. Continue it verbatim from the exact cut-off point. Do not repeat any " +
                "text that is already there, do not restart, do not add a preamble."
    }
}
