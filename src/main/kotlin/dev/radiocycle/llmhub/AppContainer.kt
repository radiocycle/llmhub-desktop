package dev.radiocycle.llmhub

import dev.radiocycle.llmhub.core.XdgPaths
import dev.radiocycle.llmhub.data.repo.ConversationRepository
import dev.radiocycle.llmhub.data.repo.ProviderRepository
import dev.radiocycle.llmhub.data.repo.SettingsRepository
import dev.radiocycle.llmhub.rotation.ChatEngine
import dev.radiocycle.llmhub.rotation.RotationEngine
import dev.radiocycle.llmhub.runtime.ChatController
import dev.radiocycle.llmhub.tools.DeleteFileTool
import dev.radiocycle.llmhub.tools.EditFileTool
import dev.radiocycle.llmhub.tools.ExecJsTool
import dev.radiocycle.llmhub.tools.JsSandbox
import dev.radiocycle.llmhub.tools.ListFilesTool
import dev.radiocycle.llmhub.tools.ReadFileTool
import dev.radiocycle.llmhub.tools.ShellTool
import dev.radiocycle.llmhub.tools.ToolRegistry
import dev.radiocycle.llmhub.tools.WebFetchTool
import dev.radiocycle.llmhub.tools.WebSearchTool
import dev.radiocycle.llmhub.tools.WorkspaceManager
import dev.radiocycle.llmhub.tools.WriteFileTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val providers = ProviderRepository(XdgPaths.configDir, scope)
    val settings = SettingsRepository(XdgPaths.configDir, scope)
    val conversations = ConversationRepository(XdgPaths.dataDir, scope)

    val rotation = RotationEngine(providers, settings)

    private val jsSandbox = JsSandbox()
    val workspace = WorkspaceManager(settings)
    val toolRegistry = ToolRegistry(
        settings = settings,
        tools = listOf(
            WebSearchTool(settings),
            WebFetchTool(settings),
            ExecJsTool(jsSandbox, settings),
            ReadFileTool(workspace, settings),
            WriteFileTool(workspace),
            EditFileTool(workspace),
            DeleteFileTool(workspace),
            ListFilesTool(workspace),
            ShellTool(workspace, settings),
        ),
    )

    val chatEngine = ChatEngine(settings, rotation, toolRegistry)
    val chatController = ChatController(scope, conversations, chatEngine)
}
