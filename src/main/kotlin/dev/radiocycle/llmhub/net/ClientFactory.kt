package dev.radiocycle.llmhub.net

import dev.radiocycle.llmhub.data.model.ApiMode

/** Stateless clients — one instance per dialect is enough. */
object ClientFactory {
    private val openAi = OpenAiClient()
    private val anthropic = AnthropicClient()
    private val google = GoogleClient()

    fun forMode(mode: ApiMode): LlmClient = when (mode) {
        ApiMode.OPENAI -> openAi
        ApiMode.ANTHROPIC -> anthropic
        ApiMode.GOOGLE -> google
    }
}
