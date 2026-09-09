package dev.radiocycle.llmhub.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

/** A tool invocation requested by the model. [argumentsJson] is a raw JSON object string. */
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

@Serializable
data class ToolResult(
    val callId: String,
    val name: String,
    val content: String,
    val isError: Boolean = false,
    val durationMs: Long = 0L,
)

/**
 * Provider-agnostic message. A TOOL message carries every result for the tool calls of the
 * assistant turn directly above it, which maps cleanly onto all three wire formats.
 */
@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String = "",
    val reasoning: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResults: List<ToolResult> = emptyList(),
    val providerName: String? = null,
    val model: String? = null,
    val error: String? = null,
    val switches: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New chat",
    val messages: List<ChatMessage> = emptyList(),
    val pinnedProviderId: String? = null,
    val pinnedModel: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class TokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
)
