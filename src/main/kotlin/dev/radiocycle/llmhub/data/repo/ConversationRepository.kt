package dev.radiocycle.llmhub.data.repo

import dev.radiocycle.llmhub.core.XdgPaths
import dev.radiocycle.llmhub.data.model.ChatMessage
import dev.radiocycle.llmhub.data.model.Conversation
import dev.radiocycle.llmhub.data.model.Role
import dev.radiocycle.llmhub.data.store.JsonFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

class ConversationRepository(
    baseDir: File = XdgPaths.dataDir,
    scope: CoroutineScope,
) {

    private val store = JsonFileStore(
        baseDir = baseDir,
        fileName = "conversations.json",
        serializer = ListSerializer(Conversation.serializer()),
        default = { emptyList() },
        scope = scope,
    )

    val conversations: StateFlow<List<Conversation>> = store.state

    fun byId(id: String?): Conversation? = id?.let { cid -> store.value.firstOrNull { it.id == cid } }

    fun create(): Conversation = Conversation().also { conversation ->
        store.update { listOf(conversation) + it }
    }

    fun delete(id: String) = store.update { list -> list.filterNot { it.id == id } }

    fun deleteAll() = store.update { emptyList() }

    fun rename(id: String, title: String) = mutate(id) { it.copy(title = title.take(80)) }

    fun setPinned(id: String, providerId: String?, model: String?) =
        mutate(id) { it.copy(pinnedProviderId = providerId, pinnedModel = model) }

    fun setMessages(id: String, messages: List<ChatMessage>) = mutate(id) { conversation ->
        val autoTitle = conversation.title
            .takeUnless { it == DEFAULT_TITLE }
            ?: messages.firstOrNull { it.role == Role.USER }?.content?.toTitle()
            ?: DEFAULT_TITLE
        conversation.copy(messages = messages, title = autoTitle)
    }

    private fun mutate(id: String, transform: (Conversation) -> Conversation) = store.update { list ->
        val updated = list.map { if (it.id == id) transform(it).copy(updatedAt = System.currentTimeMillis()) else it }
        updated.sortedByDescending { it.updatedAt }
    }

    private fun String.toTitle(): String =
        trim().replace(Regex("\\s+"), " ").take(48).ifBlank { DEFAULT_TITLE }

    private companion object {
        const val DEFAULT_TITLE = "New chat"
    }
}
