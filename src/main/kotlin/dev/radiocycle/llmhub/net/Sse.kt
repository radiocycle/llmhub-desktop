package dev.radiocycle.llmhub.net

import okhttp3.Response

/** One decoded server-sent event. */
data class SseEvent(val name: String?, val data: String)

/**
 * Reads an SSE body line by line, stopping early when [onEvent] returns false. Blocking — call it
 * from an IO dispatcher. Kept free of local helper functions so the lambda stays inlinable and can
 * therefore suspend (it emits straight into a flow).
 */
inline fun Response.readSse(onEvent: (SseEvent) -> Boolean) {
    val source = body?.source() ?: throw LlmException("Empty stream body", kind = LlmException.Kind.PARSE)
    var eventName: String? = null
    val data = StringBuilder()
    var stopped = false

    while (!stopped) {
        val line = source.readUtf8Line() ?: break
        when {
            line.isEmpty() -> {
                if (data.isNotEmpty() || eventName != null) {
                    val event = SseEvent(eventName, data.toString())
                    data.setLength(0)
                    eventName = null
                    if (!onEvent(event)) stopped = true
                }
            }
            line.startsWith(":") -> Unit // comment / keep-alive
            line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
            line.startsWith("data:") -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").removePrefix(" "))
            }
        }
    }

    if (!stopped && (data.isNotEmpty() || eventName != null)) {
        onEvent(SseEvent(eventName, data.toString()))
    }
}
