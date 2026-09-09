package dev.radiocycle.llmhub.runtime

/** Phase of the in-flight turn, in the order they occur. */
enum class Phase { Idle, Connecting, Generating, CallingTools, RunningTools, Done, Failed }

/**
 * A snapshot of what the agent is doing right now, published for the UI.
 */
data class GenerationStatus(
    val active: Boolean = false,
    val conversationId: String? = null,
    val title: String = "",
    val phase: Phase = Phase.Idle,
    val provider: String? = null,
    val toolNames: List<String> = emptyList(),
    val error: String? = null,
    val finishedAt: Long = 0L,
) {
    /** One-line description for status summary. */
    val summary: String
        get() = when (phase) {
            Phase.Idle -> "Idle"
            Phase.Connecting -> "Connecting…"
            Phase.Generating -> provider?.let { "Generating · $it" } ?: "Generating…"
            Phase.CallingTools -> if (toolNames.isEmpty()) "Calling tools…"
            else "Calling ${toolNames.joinToString(", ")}…"
            Phase.RunningTools -> when (toolNames.size) {
                0 -> "Running tools…"
                1 -> "Using ${toolNames.first()}…"
                else -> "Using ${toolNames.joinToString(", ")}…"
            }
            Phase.Done -> "Response ready"
            Phase.Failed -> error ?: "Generation failed"
        }
}
