package dev.radiocycle.llmhub.rotation

import dev.radiocycle.llmhub.core.Log
import dev.radiocycle.llmhub.data.model.Endpoint
import dev.radiocycle.llmhub.data.model.EndpointHealth
import dev.radiocycle.llmhub.data.model.Provider
import dev.radiocycle.llmhub.data.model.RotationSettings
import dev.radiocycle.llmhub.data.model.RotationStrategy
import dev.radiocycle.llmhub.data.model.TokenUsage
import dev.radiocycle.llmhub.data.model.ToolCall
import dev.radiocycle.llmhub.data.repo.ProviderRepository
import dev.radiocycle.llmhub.data.repo.SettingsRepository
import dev.radiocycle.llmhub.net.ChatRequest
import dev.radiocycle.llmhub.net.ClientFactory
import dev.radiocycle.llmhub.net.LlmException
import dev.radiocycle.llmhub.net.StreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.random.Random

sealed interface RotationEvent {
    data class Started(val endpoint: Endpoint, val model: String, val attempt: Int) : RotationEvent
    data class Text(val text: String) : RotationEvent
    data class Reasoning(val text: String) : RotationEvent
    data class Tools(val calls: List<ToolCall>) : RotationEvent
    /** A provider dropped out; [resumed] is true when partial text is being continued, not restarted. */
    data class Switched(
        val from: Provider,
        val to: Provider,
        val reason: String,
        val resumed: Boolean,
    ) : RotationEvent
    data class Finished(val endpoint: Endpoint, val model: String, val usage: TokenUsage?) : RotationEvent
    data class Failed(val message: String, val attempts: Int) : RotationEvent
}

/**
 * Schedules one turn across every provider+key pair available, streams from the chosen one, and
 * moves the same turn onward when it fails.
 *
 * Two rules shape the behaviour:
 *
 * 1. **Credential failures are silent.** 401, 402, 403 and 429 say something about the key, not the
 *    request, so the engine walks to the next key — of the same provider first, then of the next
 *    provider — without emitting anything. Nothing reaches the user until the entire pool is spent,
 *    and these attempts do not consume the retry budget, which exists for flaky transports.
 * 2. **Partial answers carry over.** If tokens were already delivered, the text so far is handed to
 *    the next endpoint as a prefill, so the reply continues rather than restarting.
 */
class RotationEngine(
    private val providers: ProviderRepository,
    private val settings: SettingsRepository,
) {
    private val _health = MutableStateFlow<Map<String, EndpointHealth>>(emptyMap())
    val health: StateFlow<Map<String, EndpointHealth>> = _health.asStateFlow()

    private val roundRobinCursor = AtomicInteger(0)

    fun healthOf(endpointId: String): EndpointHealth =
        _health.value[endpointId] ?: EndpointHealth(endpointId)

    fun clearHealth() {
        _health.value = emptyMap()
    }

    fun stream(request: ChatRequest, pinnedProviderId: String? = null): Flow<RotationEvent> = flow {
        val config = settings.current.rotation
        val pool = providers.rotationPool().flatMap { provider ->
            (0 until provider.keySlotCount).map { Endpoint(provider, it) }
        }
        if (pool.isEmpty()) {
            emit(RotationEvent.Failed("No providers configured. Add one in the Providers tab.", 0))
            return@flow
        }

        val tried = mutableSetOf<String>()
        val partial = StringBuilder()
        var attempt = 0
        var budgetSpent = 0
        var lastEndpoint: Endpoint? = null
        var lastError = ""
        var credentialFailures = 0

        while (true) {
            val endpoint = pick(pool, tried, pinnedProviderId, config)
            if (endpoint == null) {
                if (lastError.isEmpty()) lastError = "Every provider is unavailable or cooling down."
                break
            }
            tried += endpoint.id
            attempt++
            val provider = endpoint.provider

            val model = provider.modelOrDefault(
                if (provider.id == pinnedProviderId) request.model else null
            ).ifBlank { request.model }

            if (model.isBlank()) {
                lastError = "${provider.name} has no model configured."
                markFailure(endpoint, lastError, config)
                continue
            }

            // Only a change of provider is worth telling the user about; walking a key pool is not.
            lastEndpoint?.takeIf { it.provider.id != provider.id }?.let { previous ->
                emit(
                    RotationEvent.Switched(
                        from = previous.provider,
                        to = provider,
                        reason = lastError,
                        resumed = partial.isNotEmpty(),
                    )
                )
            }
            emit(RotationEvent.Started(endpoint, model, attempt))

            val attemptRequest = request.copy(
                model = model,
                tools = if (provider.supportsTools) request.tools else emptyList(),
                prefill = partial.toString().takeIf { it.isNotEmpty() && config.midStreamHandoff },
            )

            val startedAt = System.currentTimeMillis()
            try {
                var usage: TokenUsage? = null
                ClientFactory.forMode(provider.apiMode).stream(endpoint, attemptRequest).collect { event ->
                    when (event) {
                        is StreamEvent.TextDelta -> {
                            partial.append(event.text)
                            emit(RotationEvent.Text(event.text))
                        }
                        is StreamEvent.ReasoningDelta -> emit(RotationEvent.Reasoning(event.text))
                        is StreamEvent.ToolCalls -> emit(RotationEvent.Tools(event.calls))
                        is StreamEvent.Completed -> usage = event.usage
                    }
                }
                markSuccess(endpoint, System.currentTimeMillis() - startedAt)
                emit(RotationEvent.Finished(endpoint, model, usage))
                return@flow
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                val error = t as? LlmException ?: LlmException(
                    t.message ?: "unknown error",
                    kind = LlmException.Kind.NETWORK,
                    cause = t,
                )
                Log.w(TAG, "${provider.name} ${endpoint.keyLabel ?: ""} failed: ${error.message}")
                markFailure(endpoint, error.message ?: error.shortLabel, config)
                lastEndpoint = endpoint
                lastError = buildString {
                    append(provider.name)
                    endpoint.keyLabel?.let { append(" ($it)") }
                    append(": ")
                    append(error.shortLabel)
                }

                if (!shouldRotate(error, config)) {
                    emit(RotationEvent.Failed("${provider.name} — ${error.message}", attempt))
                    return@flow
                }
                if (partial.isNotEmpty() && !config.midStreamHandoff) {
                    emit(RotationEvent.Failed("${provider.name} — ${error.message} (mid-stream handoff off)", attempt))
                    return@flow
                }

                if (error.kind.isCredentialFailure) {
                    // Walk the whole pool before saying anything; this is not a transport problem.
                    credentialFailures++
                } else if (++budgetSpent >= config.maxAttempts) {
                    break
                }
            }
        }

        emit(
            RotationEvent.Failed(
                if (credentialFailures > 0 && credentialFailures == attempt) {
                    "All $attempt key(s) in the pool were rejected. Last: $lastError"
                } else {
                    "All $attempt attempt(s) failed. Last: $lastError"
                },
                attempt,
            )
        )
    }

    // --- Selection ------------------------------------------------------------------------

    /**
     * Picks the next endpoint. Strategy applies at the provider level; within a provider the keys
     * are always walked in order, so a pool behaves predictably.
     */
    private fun pick(
        pool: List<Endpoint>,
        tried: Set<String>,
        pinnedProviderId: String?,
        config: RotationSettings,
    ): Endpoint? {
        val remaining = pool.filterNot { it.id in tried }
        if (remaining.isEmpty()) return null

        if (pinnedProviderId != null) {
            // Exhaust the pinned provider's keys before falling through to the rest of the pool.
            remaining.filter { it.provider.id == pinnedProviderId }
                .minByOrNull { it.keyIndex }
                ?.let { return it }
        }

        val now = System.currentTimeMillis()
        val healthy = remaining.filterNot { healthOf(it.id).isCoolingDown(now) }
        // Everything is cooling down: take the one that recovers soonest rather than giving up.
        val candidates = healthy.ifEmpty {
            remaining.sortedBy { healthOf(it.id).cooldownUntil }.take(1)
        }

        val byProvider = candidates.groupBy { it.provider.id }
        val providersInPlay = candidates.map { it.provider }.distinctBy { it.id }

        val chosenProvider = when (config.strategy) {
            RotationStrategy.FAILOVER -> providersInPlay.first()

            RotationStrategy.ROUND_ROBIN ->
                providersInPlay[roundRobinCursor.getAndIncrement().mod(providersInPlay.size)]

            RotationStrategy.LEAST_USED -> providersInPlay.minByOrNull { provider ->
                byProvider.getValue(provider.id).minOf { healthOf(it.id).lastUsedAt }
            } ?: providersInPlay.first()

            RotationStrategy.WEIGHTED -> {
                val total = providersInPlay.sumOf { it.weight.coerceAtLeast(1) }
                var roll = Random.nextInt(total)
                providersInPlay.firstOrNull { provider ->
                    roll -= provider.weight.coerceAtLeast(1)
                    roll < 0
                } ?: providersInPlay.first()
            }
        }

        return byProvider.getValue(chosenProvider.id).minByOrNull { it.keyIndex }
    }

    private fun shouldRotate(error: LlmException, config: RotationSettings): Boolean = when (error.kind) {
        LlmException.Kind.AUTH,
        LlmException.Kind.QUOTA,
        LlmException.Kind.RATE_LIMIT -> config.rotateOnKeyError

        LlmException.Kind.SERVER,
        LlmException.Kind.NETWORK,
        LlmException.Kind.PARSE -> config.rotateOnServerError

        LlmException.Kind.MODEL_MISSING -> config.rotateOnModelMissing
        // A malformed request will fail identically everywhere; surface it instead of burning keys.
        LlmException.Kind.BAD_REQUEST -> false
        LlmException.Kind.CANCELLED -> false
    }

    // --- Health ---------------------------------------------------------------------------

    private fun markSuccess(endpoint: Endpoint, latencyMs: Long) = mutate(endpoint.id) { health ->
        health.copy(
            successes = health.successes + 1,
            consecutiveFailures = 0,
            cooldownUntil = 0L,
            lastUsedAt = System.currentTimeMillis(),
            lastLatencyMs = latencyMs,
            lastError = null,
        )
    }

    private fun markFailure(endpoint: Endpoint, reason: String, config: RotationSettings) =
        mutate(endpoint.id) { health ->
            val consecutive = health.consecutiveFailures + 1
            val backoff = min(
                config.cooldownSeconds.toLong() shl (consecutive - 1).coerceAtMost(5),
                config.maxCooldownSeconds.toLong(),
            )
            health.copy(
                failures = health.failures + 1,
                consecutiveFailures = consecutive,
                cooldownUntil = System.currentTimeMillis() + backoff * 1000,
                lastUsedAt = System.currentTimeMillis(),
                lastError = reason,
            )
        }

    private fun mutate(endpointId: String, transform: (EndpointHealth) -> EndpointHealth) {
        _health.value = _health.value.toMutableMap().apply {
            put(endpointId, transform(this[endpointId] ?: EndpointHealth(endpointId)))
        }
    }

    private companion object {
        const val TAG = "RotationEngine"
    }
}
