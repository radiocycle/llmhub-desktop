package dev.radiocycle.llmhub.tools

import dev.radiocycle.llmhub.data.repo.SettingsRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class ExecJsTool(
    private val sandbox: JsSandbox,
    private val settings: SettingsRepository,
) : AgentTool {

    override val spec = ToolSpec(
        name = "exec_js",
        description = "Execute arbitrary JavaScript code in a sandboxed runtime. Returns logs and the final expression value.",
        parameters = objectSchema(required = listOf("code")) {
            stringProp("code", "JavaScript snippet to execute.")
            intProp("timeout_ms", "Timeout in milliseconds (optional; defaults to settings).")
        },
    )

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val code = args["code"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (code.isEmpty()) return ToolOutcome("`code` is required", isError = true)

        val timeout = settings.current.tools.jsTimeoutMs
        val run = sandbox.evaluate(code, timeout)

        return buildString {
            if (run.logs.isNotEmpty()) {
                append("--- Logs ---\n")
                run.logs.forEach { appendLine(it) }
                appendLine()
            }
            if (run.error != null) {
                append("--- Error ---\n")
                appendLine(run.error)
            } else if (run.result != null) {
                append("--- Result ---\n")
                appendLine(run.result)
            } else {
                append("(script completed with undefined)")
            }
        }.let { ToolOutcome(it.trim(), isError = run.error != null) }
    }
}
