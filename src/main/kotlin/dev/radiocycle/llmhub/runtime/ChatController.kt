package dev.radiocycle.llmhub.runtime

import dev.radiocycle.llmhub.data.model.ChatMessage
import dev.radiocycle.llmhub.data.model.Role
import dev.radiocycle.llmhub.data.repo.ConversationRepository
import dev.radiocycle.llmhub.rotation.ChatEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The messages of the turn currently streaming, kept in memory so the UI renders live. */
data class LiveTurn(val conversationId: String, val messages: List<ChatMessage>)

/**
 * Runs a chat turn on an application-scoped coroutine.
 * Owns the single in-flight turn, publishes a [GenerationStatus] for the UI,
 * and persists the result to the conversation store.
 */
class ChatController(
    private val scope: CoroutineScope,
    private val conversations: ConversationRepository,
    private val chatEngine: ChatEngine,
) {
    private val _status = MutableStateFlow(GenerationStatus())
    val status: StateFlow<GenerationStatus> = _status.asStateFlow()

    private val _live = MutableStateFlow<LiveTurn?>(null)
    val live: StateFlow<LiveTurn?> = _live.asStateFlow()

    private var job: Job? = null

    val isActive: Boolean get() = _status.value.active

    /** Starts a turn. Returns false when one is already running (only one at a time). */
    fun send(
        conversationId: String,
        history: List<ChatMessage>,
        pinnedProviderId: String?,
        pinnedModel: String?,
    ): Boolean {
        if (_status.value.active) return false

        val title = conversations.byId(conversationId)?.title ?: "Chat"
        _live.value = LiveTurn(conversationId, history)
        _status.value = GenerationStatus(
            active = true,
            conversationId = conversationId,
            title = title,
            phase = Phase.Connecting,
        )

        job = scope.launch {
            var produced: List<ChatMessage> = emptyList()
            try {
                chatEngine.run(history, pinnedProviderId, pinnedModel).collect { p ->
                    produced = p
                    val messages = history + p
                    _live.value = LiveTurn(conversationId, messages)
                    _status.value = statusOf(conversationId, title, messages)
                }
                conversations.setMessages(conversationId, history + produced)
                _live.value = null
                _status.value = _status.value.copy(
                    active = false,
                    phase = Phase.Done,
                    finishedAt = System.currentTimeMillis(),
                )
            } catch (cancelled: CancellationException) {
                // User stopped: keep whatever streamed, report no completion.
                conversations.setMessages(conversationId, history + produced)
                _live.value = null
                _status.value = GenerationStatus(conversationId = conversationId, title = title)
                throw cancelled
            } catch (t: Throwable) {
                val withError = history + produced +
                    ChatMessage(role = Role.ASSISTANT, error = t.message ?: "Request failed")
                conversations.setMessages(conversationId, withError)
                _live.value = null
                _status.value = _status.value.copy(
                    active = false,
                    phase = Phase.Failed,
                    error = t.message ?: "Request failed",
                    finishedAt = System.currentTimeMillis(),
                )
            } finally {
                _live.value = null
                job = null
            }
        }
        return true
    }

    fun stop() {
        job?.cancel()
        job = null
        _live.value = null
        if (_status.value.active) {
            _status.value = _status.value.copy(active = false, phase = Phase.Idle)
        }
    }

    /** Drops the live snapshot for a conversation once the UI has read the persisted result. */
    fun clearLiveIf(conversationId: String) {
        if (_live.value?.conversationId == conversationId && !_status.value.active) {
            _live.value = null
        }
    }

    private fun statusOf(conversationId: String, title: String, messages: List<ChatMessage>): GenerationStatus {
        val last = messages.lastOrNull()
        val provider = messages.lastOrNull { it.providerName != null }?.providerName
        val phase = when {
            last == null -> Phase.Connecting
            last.role == Role.TOOL && last.toolResults.any { it.content == RUNNING } ->
                Phase.RunningTools
            last.role == Role.ASSISTANT && last.toolCalls.isNotEmpty() && last.content.isBlank() ->
                Phase.CallingTools
            last.role == Role.ASSISTANT -> Phase.Generating
            else -> Phase.Generating
        }
        val toolNames = when (phase) {
            Phase.RunningTools -> last?.toolResults?.map { it.name }?.distinct().orEmpty()
            Phase.CallingTools -> last?.toolCalls?.map { it.name }?.distinct().orEmpty()
            else -> emptyList()
        }
        return GenerationStatus(
            active = true,
            conversationId = conversationId,
            title = title,
            phase = phase,
            provider = provider,
            toolNames = toolNames,
        )
    }

    private companion object {
        const val RUNNING = "…running"
    }
}
