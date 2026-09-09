package dev.radiocycle.llmhub.net

import dev.radiocycle.llmhub.data.model.HeaderEntry
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** One shared connection pool; per-provider timeouts are cheap derived clients. */
object Http {
    val base: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val byTimeout = ConcurrentHashMap<Int, OkHttpClient>()
    private val http1ByTimeout = ConcurrentHashMap<Int, OkHttpClient>()

    fun withTimeout(seconds: Int): OkHttpClient = byTimeout.getOrPut(seconds.coerceIn(5, 900)) {
        base.newBuilder()
            .readTimeout(seconds.toLong(), TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    fun http1WithTimeout(seconds: Int): OkHttpClient = http1ByTimeout.getOrPut(seconds.coerceIn(5, 900)) {
        base.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .readTimeout(seconds.toLong(), TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Executes a request with automatic HTTP/1.1 fallback if HTTP/2 multiplexing,
     * stale pool connections, or proxy tunnels trigger a "connection closed" or "reset" error.
     */
    fun executeWithFallback(request: Request, timeoutSeconds: Int = 30): Response {
        val client = withTimeout(timeoutSeconds)
        return try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            val msg = e.message.orEmpty().lowercase()
            if (msg.contains("closed") || msg.contains("reset") || msg.contains("unexpected end of stream") || msg.contains("eof")) {
                http1WithTimeout(timeoutSeconds).newCall(request).execute()
            } else {
                throw e
            }
        }
    }
}

fun String.trimBaseUrl(): String = trim().trimEnd('/')

fun String.normalizeFirecrawlBaseUrl(): String {
    val trimmed = trim().trimEnd('/')
    return if (trimmed.endsWith("/v1")) trimmed.removeSuffix("/v1") else trimmed
}

fun Request.Builder.applyCustomHeaders(headers: List<HeaderEntry>): Request.Builder {
    headers.forEach { entry ->
        val name = entry.name.trim()
        if (name.isNotEmpty()) header(name, entry.value.trim())
    }
    return this
}

/** Maps transport-level failures onto [LlmException] kinds. */
fun Throwable.toLlmException(context: String): LlmException = when (this) {
    is LlmException -> this
    is SocketTimeoutException -> LlmException("$context: timed out", kind = LlmException.Kind.NETWORK, cause = this)
    is UnknownHostException -> LlmException("$context: host unreachable", kind = LlmException.Kind.NETWORK, cause = this)
    is SSLException -> LlmException("$context: TLS error — ${message}", kind = LlmException.Kind.NETWORK, cause = this)
    is IOException -> LlmException("$context: ${message ?: "network error"}", kind = LlmException.Kind.NETWORK, cause = this)
    else -> LlmException("$context: ${message ?: this::class.java.simpleName}", kind = LlmException.Kind.PARSE, cause = this)
}

/** Classifies an HTTP error response, reading the body for a provider-specific reason. */
fun httpFailure(response: Response, bodyText: String?): LlmException {
    val code = response.code
    val detail = bodyText?.take(400)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    val kind = when {
        code == 401 || code == 403 -> LlmException.Kind.AUTH
        code == 402 -> LlmException.Kind.QUOTA
        code == 429 -> LlmException.Kind.RATE_LIMIT
        code == 404 && detail.contains("model", ignoreCase = true) -> LlmException.Kind.MODEL_MISSING
        code == 400 && detail.contains("model", ignoreCase = true) -> LlmException.Kind.MODEL_MISSING
        code in 500..599 -> LlmException.Kind.SERVER
        else -> LlmException.Kind.BAD_REQUEST
    }
    return LlmException("HTTP $code${if (detail.isEmpty()) "" else " — $detail"}", httpCode = code, kind = kind)
}
