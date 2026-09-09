package dev.radiocycle.llmhub.tools

import dev.radiocycle.llmhub.core.AppJson
import dev.radiocycle.llmhub.data.model.ToolCall
import dev.radiocycle.llmhub.data.model.ToolResult
import dev.radiocycle.llmhub.data.repo.SettingsRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Owns the tool instances and decides which of them the model is offered on a given turn. */
class ToolRegistry(
    private val settings: SettingsRepository,
    tools: List<AgentTool>,
) {
    private val tools: Map<String, AgentTool> = tools.associateBy { it.spec.name }

    /** Specs for the tools currently switched on in settings. */
    fun activeSpecs(): List<ToolSpec> {
        val t = settings.current.tools
        val enabled = buildSet {
            if (t.webSearchEnabled) add("web_search")
            if (t.webFetchEnabled) add("web_fetch")
            if (t.execJsEnabled) add("exec_js")
            if (t.fileToolsEnabled) addAll(FILE_TOOLS)
            if (t.shellEnabled) add("shell")
        }
        return tools.values.filter { it.spec.name in enabled }.map { it.spec }
    }

    private companion object {
        val FILE_TOOLS = setOf("read_file", "write_file", "edit_file", "delete_file", "list_files")
    }

    suspend fun run(call: ToolCall): ToolResult {
        val started = System.currentTimeMillis()
        val tool = tools[call.name]
            ?: return ToolResult(call.id, call.name, "Unknown tool: ${call.name}", isError = true)

        val args = runCatching { AppJson.parseToJsonElement(call.argumentsJson).jsonObject }
            .getOrElse { JsonObject(emptyMap()) }

        val outcome = runCatching { tool.execute(args) }
            .getOrElse { ToolOutcome("Tool crashed: ${it.message}", isError = true) }

        return ToolResult(
            callId = call.id,
            name = call.name,
            content = outcome.content,
            isError = outcome.isError,
            durationMs = System.currentTimeMillis() - started,
        )
    }
}
