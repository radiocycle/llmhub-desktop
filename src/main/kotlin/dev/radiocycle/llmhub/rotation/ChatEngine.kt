package dev.radiocycle.llmhub.rotation

import dev.radiocycle.llmhub.data.model.ChatMessage
import dev.radiocycle.llmhub.data.model.Role
import dev.radiocycle.llmhub.data.model.ToolCall
import dev.radiocycle.llmhub.data.model.ToolResult
import dev.radiocycle.llmhub.data.repo.SettingsRepository
import dev.radiocycle.llmhub.net.ChatRequest
import dev.radiocycle.llmhub.tools.ToolRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Drives one user turn to completion, one round at a time:
 *
 *     generate -> tool calls -> tool output -> generate -> ...
 *
 * Each round streams an assistant turn through the [RotationEngine]. If that turn ended in tool
 * calls, they are executed, their output is appended as a TOOL message, and the loop generates
 * again with that output in context. The loop always ends on a generation: once the tool budget is
 * spent no further tools are offered, so the model has to answer in prose.
 */
class ChatEngine(
    private val settings: SettingsRepository,
    private val rotation: RotationEngine,
    private val tools: ToolRegistry,
) {
    /**
     * Emits the list of messages produced by this turn, republished after every delta so the UI can
     * render the stream. [history] is left untouched.
     */
    fun run(
        history: List<ChatMessage>,
        pinnedProviderId: String?,
        pinnedModel: String?,
    ): Flow<List<ChatMessage>> = flow {
        val appSettings = settings.current
        val produced = mutableListOf<ChatMessage>()
        // Emptied once the tool budget is spent, which forces the closing turn to be prose.
        var offeredTools = tools.activeSpecs()
        var round = 0

        while (true) {
            var assistant = ChatMessage(role = Role.ASSISTANT)
            var pendingCalls: List<ToolCall> = emptyList()
            var failed = false

            emit(produced + assistant)

            val request = ChatRequest(
                model = pinnedModel.orEmpty(),
                messages = history + produced,
                system = appSettings.systemPrompt.takeIf { it.isNotBlank() },
                tools = offeredTools,
                temperature = appSettings.temperature,
                maxTokens = appSettings.maxTokens,
            )

            rotation.stream(request, pinnedProviderId).collect { event ->
                when (event) {
                    is RotationEvent.Started -> {
                        assistant = assistant.copy(
                            providerName = event.endpoint.provider.name,
                            model = event.model,
                        )
                    }

                    is RotationEvent.Text -> {
                        assistant = assistant.copy(content = assistant.content + event.text)
                    }

                    is RotationEvent.Reasoning -> {
                        assistant = assistant.copy(reasoning = assistant.reasoning + event.text)
                    }

                    is RotationEvent.Switched -> {
                        val note = buildString {
                            append("${event.from.name} → ${event.to.name}")
                            if (event.reason.isNotBlank()) append(" · ${event.reason.substringAfter(": ")}")
                            if (event.resumed) append(" · resumed")
                        }
                        assistant = assistant.copy(
                            switches = assistant.switches + note,
                            // A provider that restarts from scratch invalidates the partial text.
                            content = if (event.resumed) assistant.content else "",
                        )
                    }

                    is RotationEvent.Tools -> pendingCalls = event.calls

                    is RotationEvent.Finished -> {
                        assistant = assistant.copy(
                            providerName = event.endpoint.provider.name,
                            model = event.model,
                        )
                    }

                    is RotationEvent.Failed -> {
                        assistant = assistant.copy(error = event.message)
                        failed = true
                    }
                }
                emit(produced + assistant)
            }

            if (pendingCalls.isNotEmpty()) assistant = assistant.copy(toolCalls = pendingCalls)
            produced += assistant
            emit(produced.toList())

            if (failed || pendingCalls.isEmpty()) break

            round++
            var toolMessage = ChatMessage(
                role = Role.TOOL,
                toolResults = pendingCalls.map { call ->
                    ToolResult(call.id, call.name, "…running", isError = false)
                },
            )
            produced += toolMessage
            emit(produced.toList())

            val results = coroutineScope {
                pendingCalls.map { call -> async { tools.run(call) } }.map { it.await() }
            }
            toolMessage = toolMessage.copy(toolResults = results)
            produced[produced.lastIndex] = toolMessage
            emit(produced.toList())

            // Budget spent: loop once more with no tools on offer, so the results just gathered are
            // turned into a written answer rather than another round of calls.
            if (round >= appSettings.tools.maxToolIterations) offeredTools = emptyList()
        }
    }
}
