package dev.radiocycle.llmhub.tools

import dev.radiocycle.llmhub.data.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs a shell command inside the workspace on Linux.
 * Uses /bin/bash (falling back to /bin/sh).
 */
class ShellTool(
    private val workspace: WorkspaceManager,
    private val settings: SettingsRepository,
) : AgentTool {

    override val spec = ToolSpec(
        name = "shell",
        description = "Run a shell command in the workspace directory and return its combined " +
            "stdout and stderr plus the exit code. A standard Linux shell (/bin/bash) is available. " +
            "State does not persist between calls, so chain steps with && or ; and cd within a single command.",
        parameters = objectSchema(required = listOf("command")) {
            stringProp("command", "The command line to execute, e.g. `ls -la` or `grep -r foo .`.")
            stringProp("cwd", "Working directory for this command (optional; defaults to workspace).")
            intProp("timeout_ms", "Execution budget in milliseconds (optional).")
        },
    )

    override suspend fun execute(args: JsonObject): ToolOutcome = withContext(Dispatchers.IO) {
        val command = args["command"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (command.isEmpty()) return@withContext ToolOutcome("`command` is required", isError = true)

        val toolSettings = settings.current.tools
        val timeout = (args["timeout_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: toolSettings.shellTimeoutMs).coerceIn(500L, 600_000L)

        val workingDir = runCatching {
            args["cwd"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?.let { workspace.resolve(it) } ?: workspace.root()
        }.getOrElse { return@withContext ToolOutcome(it.message ?: "bad cwd", isError = true) }

        val shellBin = if (File("/bin/bash").exists()) "/bin/bash" else "/bin/sh"
        val script = "cd ${shellQuote(workingDir.absolutePath)} && ($command)"
        val builder = ProcessBuilder(shellBin, "-c", script)
            .directory(workingDir)
            .redirectErrorStream(true)

        runCatching {
            val process = builder.start()
            process.outputStream.close()
            val output = StringBuilder()
            val reader = process.inputStream.bufferedReader()
            val readerThread = Thread {
                runCatching {
                    reader.forEachLine { line ->
                        synchronized(output) {
                            if (output.length < MAX_OUTPUT) output.append(line).append('\n')
                        }
                    }
                }
            }.apply { isDaemon = true; start() }

            val finished = process.waitFor(timeout, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                readerThread.join(500)
                return@runCatching ToolOutcome(
                    render(workingDir, output, exit = null, timedOut = timeout),
                    isError = true,
                )
            }
            readerThread.join(1000)
            val exit = process.exitValue()
            ToolOutcome(render(workingDir, output, exit, timedOut = null), isError = exit != 0)
        }.getOrElse { ToolOutcome("shell failed: ${it.message}", isError = true) }
    }

    private fun render(
        cwd: File,
        output: StringBuilder,
        exit: Int?,
        timedOut: Long?,
    ): String = buildString {
        append("# bash @ ").append(workspace.label(cwd))
        when {
            timedOut != null -> append("  · timed out after ${timedOut}ms")
            exit != null -> append("  · exit $exit")
        }
        appendLine()
        val body = synchronized(output) { output.toString() }.trimEnd()
        if (body.isEmpty()) append("(no output)") else append(body)
        if (body.length >= MAX_OUTPUT - 1) append("\n… output truncated")
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private companion object {
        const val MAX_OUTPUT = 60_000
    }
}
