package dev.radiocycle.llmhub.net

import dev.radiocycle.llmhub.core.AppJson
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
import kotlinx.serialization.json.JsonElement
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

/** Google Generative Language (Gemini) dialect. */
class GoogleClient : LlmClient {

    override fun stream(endpoint: Endpoint, request: ChatRequest): Flow<StreamEvent> = flow {
        val provider = endpoint.provider
        val model = request.model.removePrefix("models/")
        val url = "${provider.baseUrl.trimBaseUrl()}/v1beta/models/$model:streamGenerateContent?alt=sse"
        val payload = buildPayload(provider, request)
        val call = Http.withTimeout(provider.timeoutSeconds).newCall(
            Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(OpenAiClient.JSON_MEDIA))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .apply { if (endpoint.apiKey.isNotBlank()) header("x-goog-api-key", endpoint.apiKey) }
                .applyCustomHeaders(provider.headers)
                .build()
        )

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw httpFailure(response, response.body?.string())

                val toolCalls = mutableListOf<ToolCall>()
                var finish: String? = null
                var usage: TokenUsage? = null

                response.readSse { event ->
                    coroutineContext.ensureActive()
                    if (event.data.isBlank()) return@readSse true
                    val root = runCatching { AppJson.parseToJsonElement(event.data).jsonObject }.getOrNull()
                        ?: return@readSse true

                    root["error"]?.jsonObject?.let { error ->
                        val code = error["code"]?.jsonPrimitive?.intOrNull
                        throw LlmException(
                            error["message"]?.jsonPrimitive?.contentOrNull ?: "provider error",
                            httpCode = code,
                            kind = when (code) {
                                401, 403 -> LlmException.Kind.AUTH
                                429 -> LlmException.Kind.RATE_LIMIT
                                404 -> LlmException.Kind.MODEL_MISSING
                                in 500..599 -> LlmException.Kind.SERVER
                                else -> LlmException.Kind.BAD_REQUEST
                            },
                        )
                    }

                    root["usageMetadata"]?.jsonObject?.let { u ->
                        usage = TokenUsage(
                            inputTokens = u["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: 0,
                            outputTokens = u["candidatesTokenCount"]?.jsonPrimitive?.intOrNull ?: 0,
                        )
                    }

                    val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?: return@readSse true
                    candidate["finishReason"]?.jsonPrimitive?.contentOrNull?.let { finish = it }

                    candidate["content"]?.jsonObject?.get("parts")?.jsonArray?.forEach { element ->
                        val part = element.jsonObject
                        val isThought = part["thought"]?.jsonPrimitive?.contentOrNull == "true"
                        part["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { text ->
                            emit(if (isThought) StreamEvent.ReasoningDelta(text) else StreamEvent.TextDelta(text))
                        }
                        part["functionCall"]?.jsonObject?.let { fn ->
                            val name = fn["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            if (name.isNotBlank()) {
                                toolCalls += ToolCall(
                                    id = "gcall_${name}_${toolCalls.size}_${System.nanoTime()}",
                                    name = name,
                                    argumentsJson = (fn["args"] ?: JsonObject(emptyMap())).toString(),
                                )
                            }
                        }
                    }
                    true
                }

                if (toolCalls.isNotEmpty()) emit(StreamEvent.ToolCalls(toolCalls))
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
            .url("${provider.baseUrl.trimBaseUrl()}/v1beta/models?pageSize=200")
            .apply { if (endpoint.apiKey.isNotBlank()) header("x-goog-api-key", endpoint.apiKey) }
            .applyCustomHeaders(provider.headers)
            .build()
        try {
            Http.withTimeout(30).newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw httpFailure(response, body)
                AppJson.parseToJsonElement(body).jsonObject["models"]?.jsonArray
                    ?.filter { element ->
                        element.jsonObject["supportedGenerationMethods"]?.jsonArray
                            ?.any { it.jsonPrimitive.contentOrNull == "generateContent" } ?: true
                    }
                    ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") }
                    ?.sorted()
                    .orEmpty()
            }
        } catch (t: Throwable) {
            throw t.toLlmException(provider.name)
        }
    }

    private fun buildPayload(provider: Provider, request: ChatRequest): JsonObject = buildJsonObject {
        put("contents", buildContents(request))
        val system = buildString {
            request.system?.takeIf { it.isNotBlank() }?.let { append(it) }
            if (request.prefill != null) {
                if (isNotEmpty()) append("\n\n")
                append(OpenAiClient.CONTINUATION_HINT)
            }
        }
        if (system.isNotBlank()) {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", system) } }
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", request.temperature)
            put("maxOutputTokens", request.maxTokens)

            val customVal = provider.effectiveEffortValue
            val customBudget = provider.customEffort.toIntOrNull()

            if (provider.effortParameter.isNotBlank()) {
                if (!customVal.isNullOrBlank()) {
                    val intVal = customVal.toIntOrNull()
                    if (intVal != null) put(provider.effortParameter.trim(), intVal)
                    else put(provider.effortParameter.trim(), customVal)
                }
            } else if (customBudget != null) {
                putJsonObject("thinkingConfig") { put("thinkingBudget", customBudget) }
            } else {
                when (provider.reasoningEffort) {
                    ReasoningEffort.NONE -> putJsonObject("thinkingConfig") { put("thinkingBudget", 0) }
                    ReasoningEffort.LOW -> putJsonObject("thinkingConfig") { put("thinkingBudget", 1024) }
                    ReasoningEffort.MEDIUM -> putJsonObject("thinkingConfig") { put("thinkingBudget", 2048) }
                    ReasoningEffort.HIGH -> putJsonObject("thinkingConfig") { put("thinkingBudget", 4096) }
                    ReasoningEffort.DEFAULT -> Unit
                }
            }
        }
        if (request.tools.isNotEmpty()) {
            putJsonArray("tools") {
                addJsonObject {
                    putJsonArray("functionDeclarations") {
                        request.tools.forEach { tool ->
                            addJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", sanitizeSchema(tool.parameters))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildContents(request: ChatRequest): JsonArray = buildJsonArray {
        request.messages.forEach { message ->
            when (message.role) {
                Role.SYSTEM -> Unit
                Role.USER -> if (message.content.isNotBlank()) {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { addJsonObject { put("text", message.content) } }
                    }
                }

                Role.ASSISTANT -> addJsonObject {
                    put("role", "model")
                    putJsonArray("parts") {
                        if (message.content.isNotBlank()) addJsonObject { put("text", message.content) }
                        message.toolCalls.forEach { call ->
                            addJsonObject {
                                putJsonObject("functionCall") {
                                    put("name", call.name)
                                    put("args", runCatching {
                                        AppJson.parseToJsonElement(call.argumentsJson).jsonObject
                                    }.getOrElse { JsonObject(emptyMap()) })
                                }
                            }
                        }
                    }
                }

                Role.TOOL -> addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        message.toolResults.forEach { result ->
                            addJsonObject {
                                putJsonObject("functionResponse") {
                                    put("name", result.name)
                                    putJsonObject("response") {
                                        put(if (result.isError) "error" else "result", result.content)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        request.prefill?.takeIf { it.isNotBlank() }?.let { partial ->
            addJsonObject {
                put("role", "model")
                putJsonArray("parts") { addJsonObject { put("text", partial) } }
            }
        }
    }

    /** Gemini rejects several standard JSON-Schema keywords, so they are stripped recursively. */
    private fun sanitizeSchema(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> buildJsonObject {
            element.forEach { (key, value) ->
                if (key !in UNSUPPORTED_SCHEMA_KEYS) put(key, sanitizeSchema(value))
            }
        }
        is JsonArray -> buildJsonArray { element.forEach { add(sanitizeSchema(it)) } }
        else -> element
    }

    private companion object {
        val UNSUPPORTED_SCHEMA_KEYS = setOf(
            "\$schema", "additionalProperties", "exclusiveMinimum", "exclusiveMaximum",
            "patternProperties", "const", "examples", "definitions", "\$defs", "\$ref",
        )
    }
}
