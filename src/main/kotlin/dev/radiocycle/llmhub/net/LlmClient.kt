package dev.radiocycle.llmhub.net

import dev.radiocycle.llmhub.data.model.ChatMessage
import dev.radiocycle.llmhub.data.model.Endpoint
import dev.radiocycle.llmhub.data.model.TokenUsage
import dev.radiocycle.llmhub.data.model.ToolCall
import dev.radiocycle.llmhub.tools.ToolSpec
import kotlinx.coroutines.flow.Flow

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val system: String? = null,
    val tools: List<ToolSpec> = emptyList(),
    val temperature: Float = 1.0f,
    val maxTokens: Int = 4096,
    /**
     * Partial assistant text produced by a previous provider before it died. Clients append it as
     * a trailing assistant turn so generation resumes instead of restarting.
     */
    val prefill: String? = null,
)

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class ReasoningDelta(val text: String) : StreamEvent
    data class ToolCalls(val calls: List<ToolCall>) : StreamEvent
    data class Completed(val finishReason: String?, val usage: TokenUsage?) : StreamEvent
}

/**
 * Thrown by every client so the rotation engine can decide, protocol-independently, whether to
 * fail over to the next endpoint.
 */
class LlmException(
    message: String,
    val httpCode: Int? = null,
    val kind: Kind,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Kind {
        /** 401 / 403 — the credential is rejected. */
        AUTH,
        /** 402 — the credential is out of funds or over its quota. */
        QUOTA,
        /** 429 — the credential is rate limited. */
        RATE_LIMIT,
        SERVER, NETWORK, MODEL_MISSING, BAD_REQUEST, PARSE, CANCELLED;

        /** True when the failure is a property of the credential, not of the request. */
        val isCredentialFailure: Boolean get() = this == AUTH || this == QUOTA || this == RATE_LIMIT
    }

    val shortLabel: String
        get() = buildString {
            append(kind.name.lowercase().replace('_', ' '))
            httpCode?.let { append(" ($it)") }
        }
}

interface LlmClient {
    /** Streams one assistant turn through one provider+key pair. Throws [LlmException] on failure. */
    fun stream(endpoint: Endpoint, request: ChatRequest): Flow<StreamEvent>

    /** Queries the provider's model catalogue. Returns an empty list if unsupported. */
    suspend fun listModels(endpoint: Endpoint): List<String>
}
