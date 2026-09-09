package dev.radiocycle.llmhub.tools

import dev.radiocycle.llmhub.core.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit

data class JsRunResult(
    val result: String? = null,
    val logs: List<String> = emptyList(),
    val error: String? = null,
)

class JsSandbox {
    private val nodeBin: String? by lazy {
        listOf("node", "/usr/local/bin/node", "/usr/bin/node", "bun", "deno", "qjs")
            .firstOrNull { bin ->
                runCatching {
                    val p = ProcessBuilder(bin, "-v").start()
                    p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
                }.getOrDefault(false)
            }
    }

    suspend fun evaluate(code: String, timeoutMs: Long): JsRunResult = withContext(Dispatchers.IO) {
        val bin = nodeBin ?: return@withContext JsRunResult(
            error = "JavaScript runtime (Node.js/Bun/Deno) is not installed on this system.",
        )

        val harness = """
            const __logs = [];
            function __show(v) {
                if (typeof v === 'string') return v;
                if (v instanceof Error) return (v.stack || (v.name + ': ' + v.message));
                if (typeof v === 'undefined') return 'undefined';
                if (typeof v === 'function') return v.toString();
                try { return JSON.stringify(v, null, 2); } catch (e) { return String(v); }
            }
            const console = {
                log: (...args) => __logs.push(args.map(__show).join(' ')),
                info: (...args) => __logs.push(args.map(__show).join(' ')),
                warn: (...args) => __logs.push(args.map(__show).join(' ')),
                error: (...args) => __logs.push(args.map(__show).join(' ')),
                debug: (...args) => __logs.push(args.map(__show).join(' '))
            };
            (async () => {
                try {
                    const __out = eval(${AppJson.encodeToString(kotlinx.serialization.builtins.serializer(), code)});
                    const res = (__out && typeof __out.then === 'function') ? await __out : __out;
                    process.stdout.write(JSON.stringify({ logs: __logs, result: __show(res), error: null }));
                } catch (e) {
                    process.stdout.write(JSON.stringify({ logs: __logs, result: null, error: __show(e) }));
                }
            })();
        """.trimIndent()

        runCatching {
            val process = ProcessBuilder(bin, "-e", harness).redirectErrorStream(false).start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@runCatching JsRunResult(error = "Execution timed out after ${timeoutMs}ms")
            }
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val stderr = process.errorStream.bufferedReader().readText().trim()

            if (stdout.isNotEmpty()) {
                val root = runCatching { AppJson.parseToJsonElement(stdout).jsonObject }.getOrNull()
                if (root != null) {
                    JsRunResult(
                        result = root["result"]?.jsonPrimitive?.contentOrNull,
                        logs = root["logs"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                        error = root["error"]?.jsonPrimitive?.contentOrNull ?: stderr.ifBlank { null },
                    )
                } else {
                    JsRunResult(result = stdout, error = stderr.ifBlank { null })
                }
            } else {
                JsRunResult(error = stderr.ifBlank { "Unknown script execution error (exit code ${process.exitValue()})" })
            }
        }.getOrElse { JsRunResult(error = "Sandbox failed: ${it.message}") }
    }
}
