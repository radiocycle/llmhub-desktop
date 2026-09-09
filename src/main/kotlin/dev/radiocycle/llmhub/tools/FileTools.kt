package dev.radiocycle.llmhub.tools

import dev.radiocycle.llmhub.data.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class ReadFileTool(
    private val workspace: WorkspaceManager,
    private val settings: SettingsRepository,
) : AgentTool {
    override val spec = ToolSpec(
        name = "read_file",
        description = "Read lines from a text file in the workspace. Supports slicing by line offset.",
        parameters = objectSchema(required = listOf("path")) {
            stringProp("path", "File path relative to the workspace, or an absolute path.")
            intProp("offset", "1-based line number to start reading from (optional; defaults to 1).")
            intProp("limit", "Maximum number of lines to return (optional; defaults to 200).")
        },
    )

    override suspend fun execute(args: JsonObject): ToolOutcome = withContext(Dispatchers.IO) {
        val path = args["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (path.isEmpty()) return@withContext ToolOutcome("`path` is required", isError = true)

        val file = runCatching { workspace.resolve(path) }
            .getOrElse { return@withContext ToolOutcome(it.message ?: "bad path", isError = true) }
        if (!file.exists()) return@withContext ToolOutcome("file does not exist: $path", isError = true)
        if (file.isDirectory) return@withContext ToolOutcome("$path is a directory; use list_files", isError = true)

        val offset = (args["offset"]?.jsonPrimitive?.intOrNull ?: 1).coerceAtLeast(1)
        val limit = (args["limit"]?.jsonPrimitive?.intOrNull ?: 200).coerceIn(1, 1000)

        runCatching {
            val lines = file.bufferedReader().useLines { seq ->
                seq.drop(offset - 1).take(limit).toList()
            }
            val formatted = lines.mapIndexed { idx, line ->
                val lineNo = offset + idx
                "$lineNo: $line"
            }.joinToString("\n")
            ToolOutcome(formatted.ifEmpty { "(file is empty at line $offset)" })
        }.getOrElse { ToolOutcome("could not read $path: ${it.message}", isError = true) }
    }
}

class WriteFileTool(private val workspace: WorkspaceManager) : AgentTool {
    override val spec = ToolSpec(
        name = "write_file",
        description = "Create or completely overwrite a file in the workspace with new content.",
        parameters = objectSchema(required = listOf("path", "content")) {
            stringProp("path", "File path relative to the workspace, or an absolute path.")
            stringProp("content", "Full file contents to write.")
        },
    )

    override suspend fun execute(args: JsonObject): ToolOutcome = withContext(Dispatchers.IO) {
        val path = args["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val content = args["content"]?.jsonPrimitive?.contentOrNull
        if (path.isEmpty()) return@withContext ToolOutcome("`path` is required", isError = true)
        if (content == null) return@withContext ToolOutcome("`content` is required", isError = true)

        val file = runCatching { workspace.resolve(path) }
            .getOrElse { return@withContext ToolOutcome(it.message ?: "bad path", isError = true) }

        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(content)
            ToolOutcome("wrote ${content.length} characters to ${workspace.label(file)}")
        }.getOrElse { ToolOutcome("could not write $path: ${it.message}", isError = true) }
    }
}

class EditFileTool(private val workspace: WorkspaceManager) : AgentTool {
    override val spec = ToolSpec(
        name = "edit_file",
        description = "Replace a unique target chunk in a file with replacement content.",
        parameters = objectSchema(required = listOf("path", "target", "replacement")) {
            stringProp("path", "File path relative to the workspace, or an absolute path.")
            stringProp("target", "Exact text chunk in the file to be replaced.")
            stringProp("replacement", "New content to replace the target chunk.")
            booleanProp("multiple", "If true, replace all occurrences of target (optional).")
        },
    )

    override suspend fun execute(args: JsonObject): ToolOutcome = withContext(Dispatchers.IO) {
        val path = args["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val target = args["target"]?.jsonPrimitive?.contentOrNull
        val replacement = args["replacement"]?.jsonPrimitive?.contentOrNull
        val multiple = args["multiple"]?.jsonPrimitive?.booleanOrNull ?: false

        if (path.isEmpty()) return@withContext ToolOutcome("`path` is required", isError = true)
        if (target.isNullOrEmpty()) return@withContext ToolOutcome("`target` is required", isError = true)
        if (replacement == null) return@withContext ToolOutcome("`replacement` is required", isError = true)

        val file = runCatching { workspace.resolve(path) }
            .getOrElse { return@withContext ToolOutcome(it.message ?: "bad path", isError = true) }
        if (!file.exists()) return@withContext ToolOutcome("file does not exist: $path", isError = true)

        runCatching {
            val content = file.readText()
            if (!content.contains(target)) {
                return@runCatching ToolOutcome("target chunk was not found in $path", isError = true)
            }
            val count = content.split(target).size - 1
            if (count > 1 && !multiple) {
                return@runCatching ToolOutcome(
                    "target chunk occurs $count times in $path; make target more specific or set multiple=true",
                    isError = true,
                )
            }
            val updated = if (multiple) content.replace(target, replacement) else content.replaceFirst(target, replacement)
            file.writeText(updated)
            ToolOutcome("successfully replaced $count occurrence(s) in ${workspace.label(file)}")
        }.getOrElse { ToolOutcome("could not edit $path: ${it.message}", isError = true) }
    }
}

class DeleteFileTool(private val workspace: WorkspaceManager) : AgentTool {
    override val spec = ToolSpec(
        name = "delete_file",
        description = "Delete a file or empty directory in the workspace.",
        parameters = objectSchema(required = listOf("path")) {
            stringProp("path", "File path relative to the workspace, or an absolute path.")
        },
    )

    override suspend fun execute(args: JsonObject): ToolOutcome = withContext(Dispatchers.IO) {
        val path = args["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (path.isEmpty()) return@withContext ToolOutcome("`path` is required", isError = true)

        val file = runCatching { workspace.resolve(path) }
            .getOrElse { return@withContext ToolOutcome(it.message ?: "bad path", isError = true) }
        if (!file.exists()) return@withContext ToolOutcome("file does not exist: $path", isError = true)

        runCatching {
            val ok = file.delete()
            if (ok) ToolOutcome("deleted ${workspace.label(file)}")
            else ToolOutcome("failed to delete ${workspace.label(file)}", isError = true)
        }.getOrElse { ToolOutcome("could not delete $path: ${it.message}", isError = true) }
    }
}

class ListFilesTool(private val workspace: WorkspaceManager) : AgentTool {
    override val spec = ToolSpec(
        name = "list_files",
        description = "List files and subdirectories in the workspace.",
        parameters = objectSchema {
            stringProp("path", "Directory path relative to workspace (optional; defaults to workspace root).")
            booleanProp("recursive", "Whether to list recursively (optional; defaults to false).")
            intProp("limit", "Maximum items to return (optional; defaults to 100).")
        },
    )

    override suspend fun execute(args: JsonObject): ToolOutcome = withContext(Dispatchers.IO) {
        val path = args["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val recursive = args["recursive"]?.jsonPrimitive?.booleanOrNull ?: false
        val limit = (args["limit"]?.jsonPrimitive?.intOrNull ?: 100).coerceIn(1, 500)

        val dir = runCatching {
            if (path.isNotEmpty()) workspace.resolve(path) else workspace.root()
        }.getOrElse { return@withContext ToolOutcome(it.message ?: "bad path", isError = true) }

        if (!dir.exists()) return@withContext ToolOutcome("directory does not exist: $path", isError = true)
        if (!dir.isDirectory) return@withContext ToolOutcome("$path is a file, not a directory", isError = true)

        runCatching {
            val files = if (recursive) {
                dir.walkTopDown().maxDepth(6).take(limit).toList()
            } else {
                dir.listFiles()?.take(limit).orEmpty()
            }
            val formatted = files.joinToString("\n") { f ->
                val type = if (f.isDirectory) "[dir] " else "[file]"
                "$type ${workspace.label(f)}"
            }
            ToolOutcome(formatted.ifEmpty { "(directory is empty)" })
        }.getOrElse { ToolOutcome("could not list files: ${it.message}", isError = true) }
    }
}
