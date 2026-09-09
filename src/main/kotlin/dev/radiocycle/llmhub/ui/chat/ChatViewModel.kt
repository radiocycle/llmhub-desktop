package dev.radiocycle.llmhub.ui.chat

import dev.radiocycle.llmhub.AppContainer
import dev.radiocycle.llmhub.data.model.AppSettings
import dev.radiocycle.llmhub.data.model.ChatMessage
import dev.radiocycle.llmhub.data.model.Conversation
import dev.radiocycle.llmhub.data.model.Provider
import dev.radiocycle.llmhub.data.model.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ChatUiState(
    val conversationId: String? = null,
    val title: String = "New chat",
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val pinnedProviderId: String? = null,
    val pinnedModel: String? = null,
    val notice: String? = null,
)

private data class OpenState(
    val conversationId: String? = null,
    val pinnedProviderId: String? = null,
    val pinnedModel: String? = null,
    val notice: String? = null,
)

class ChatViewModel(private val container: AppContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val controller = container.chatController

    private val _open = MutableStateFlow(OpenState())

    val conversations: StateFlow<List<Conversation>> = container.conversations.conversations
    val providers: StateFlow<List<Provider>> = container.providers.providers
    val settings: StateFlow<AppSettings> = container.settings.settings

    val state: StateFlow<ChatUiState> = combine(
        _open,
        container.conversations.conversations,
        controller.live,
        controller.status,
    ) { open, conversations, live, status ->
        val conversation = conversations.firstOrNull { it.id == open.conversationId }
        val liveHere = live?.takeIf { it.conversationId == open.conversationId }
        ChatUiState(
            conversationId = open.conversationId,
            title = conversation?.title ?: "New chat",
            messages = liveHere?.messages ?: conversation?.messages ?: emptyList(),
            isStreaming = status.active && status.conversationId == open.conversationId,
            pinnedProviderId = open.pinnedProviderId,
            pinnedModel = open.pinnedModel,
            notice = open.notice,
        )
    }.stateIn(scope, SharingStarted.Eagerly, ChatUiState())

    init {
        val existing = container.conversations.conversations.value.firstOrNull()
        if (existing != null) open(existing.id) else newChat()
    }

    fun open(conversationId: String) {
        val conversation = container.conversations.byId(conversationId) ?: return
        _open.value = OpenState(
            conversationId = conversation.id,
            pinnedProviderId = conversation.pinnedProviderId,
            pinnedModel = conversation.pinnedModel,
        )
    }

    fun newChat() {
        val previous = _open.value
        val conversation = container.conversations.create()
        _open.value = OpenState(
            conversationId = conversation.id,
            pinnedProviderId = previous.pinnedProviderId,
            pinnedModel = previous.pinnedModel,
        )
        persistPin()
    }

    fun deleteConversation(id: String) {
        if (controller.status.value.conversationId == id) controller.stop()
        container.conversations.delete(id)
        if (_open.value.conversationId == id) {
            val next = container.conversations.conversations.value.firstOrNull { it.id != id }
            if (next != null) open(next.id) else newChat()
        }
    }

    fun rename(title: String) {
        val id = _open.value.conversationId ?: return
        container.conversations.rename(id, title)
    }

    fun pin(providerId: String?, model: String?) {
        _open.update { it.copy(pinnedProviderId = providerId, pinnedModel = model) }
        persistPin()
    }

    fun dismissNotice() {
        _open.update { it.copy(notice = null) }
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty()) return

        if (controller.isActive) {
            _open.update { it.copy(notice = "Already generating — stop it first or wait.") }
            return
        }
        if (container.providers.rotationPool().isEmpty()) {
            _open.update { it.copy(notice = "Add and enable at least one provider before sending.") }
            return
        }

        val open = _open.value
        val conversationId = open.conversationId ?: container.conversations.create().id
        if (open.conversationId == null) {
            _open.update { it.copy(conversationId = conversationId) }
        }

        val conversation = container.conversations.byId(conversationId)
        val current = conversation?.messages.orEmpty()
        val history = current + ChatMessage(role = Role.USER, content = prompt)
        container.conversations.setMessages(conversationId, history)
        _open.update { it.copy(notice = null) }
        controller.send(conversationId, history, open.pinnedProviderId, open.pinnedModel)
    }

    fun stop() = controller.stop()

    fun retryLast() {
        if (controller.isActive) return
        val conversationId = _open.value.conversationId ?: return
        val conversation = container.conversations.byId(conversationId) ?: return
        val messages = conversation.messages
        val lastUser = messages.indexOfLast { it.role == Role.USER }
        if (lastUser < 0) return
        val prompt = messages[lastUser].content
        val trimmed = messages.take(lastUser)
        container.conversations.setMessages(conversationId, trimmed)
        val history = trimmed + ChatMessage(role = Role.USER, content = prompt)
        container.conversations.setMessages(conversationId, history)
        controller.send(conversationId, history, _open.value.pinnedProviderId, _open.value.pinnedModel)
    }

    fun deleteAllConversations() {
        if (controller.isActive) controller.stop()
        container.conversations.deleteAll()
        newChat()
    }

    private fun persistPin() {
        val open = _open.value
        open.conversationId?.let {
            container.conversations.setPinned(it, open.pinnedProviderId, open.pinnedModel)
        }
    }
}
