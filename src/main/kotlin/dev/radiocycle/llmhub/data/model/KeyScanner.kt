package dev.radiocycle.llmhub.data.model

/**
 * Key scanner and provider detector ported from KeyScanner (lines 532-555).
 * Detects API key formats using provider signature regexes and routes them
 * to the appropriate basic provider presets (excluding custom and local).
 */
object KeyScanner {

    /**
     * Master regex matching known API key patterns ported from KeyScanner:
     * - OpenAI (sk-..., sk-proj-...)
     * - Alibaba DashScope (sk-ws-...)
     * - Anthropic (sk-ant-api..., sk-ant-admin...)
     * - OpenRouter (sk-or-v1-...)
     * - Google Gemini (AIza...)
     * - Groq (gsk_...)
     * - HuggingFace (hf_...)
     * - Replicate (r8_...)
     * - Cohere (co-..., TKN-..., AUTH_TOKEN=...)
     * - Perplexity (pplx-..., pa-...)
     * - Voyage AI (voyage-..., vp-...)
     * - Cerebras (csk_...)
     * - Together AI (together_...)
     * - Fireworks (fw_...)
     * - xAI Grok (xai-...)
     */
    val COMBINED_KEY_REGEX = Regex(
        """\b(""" +
            """sk-ws-[A-Za-z0-9._\-]{30,}|""" +
            """sk-[a-zA-Z0-9\-_]{20,}|""" +
            """sk-proj-[a-zA-Z0-9\-_]{20,}|""" +
            """sk-ant-(?:api|admin)[a-zA-Z0-9\-_]{20,}|""" +
            """sk-or-v1-[a-zA-Z0-9]{40,}|""" +
            """AIza[0-9A-Za-z\-_]{35}|""" +
            """gsk_[a-zA-Z0-9]{20,}|""" +
            """hf_[a-zA-Z0-9]{20,}|""" +
            """r8_[a-zA-Z0-9]{36}|""" +
            """co-[a-zA-Z0-9\-]{36,}|""" +
            """TKN-[a-zA-Z0-9\-]{20,}|""" +
            """AUTH_TOKEN=[a-zA-Z0-9\-]{20,}|""" +
            """pa-[a-zA-Z0-9\-_]{40,}|""" +
            """voyage-[a-zA-Z0-9]{16,}|""" +
            """vp-[a-zA-Z0-9]{16,}|""" +
            """csk_[a-zA-Z0-9_-]{16,}|""" +
            """pplx-[a-zA-Z0-9]{16,}|""" +
            """together_[a-zA-Z0-9]{20,}|""" +
            """fw_[a-zA-Z0-9]{20,}|""" +
            """xai-[a-zA-Z0-9_-]{20,}""" +
            """)\b"""
    )

    private val ANTHROPIC_REGEX = Regex("""^sk-ant-(?:api|admin)[a-zA-Z0-9\-_]{20,}$""")
    private val OPENROUTER_REGEX = Regex("""^sk-or-v1-[a-zA-Z0-9]{40,}$""")
    private val GOOGLE_REGEX = Regex("""^AIza[0-9A-Za-z\-_]{35}$""")
    private val GROQ_REGEX = Regex("""^gsk_[a-zA-Z0-9]{20,}$""")
    private val CEREBRAS_REGEX = Regex("""^csk_[a-zA-Z0-9_-]{16,}$""")
    private val PERPLEXITY_REGEX = Regex("""^(?:pplx-[a-zA-Z0-9]{16,}|pa-[a-zA-Z0-9\-_]{40,})$""")
    private val TOGETHER_REGEX = Regex("""^together_[a-zA-Z0-9]{20,}$""")
    private val FIREWORKS_REGEX = Regex("""^fw_[a-zA-Z0-9]{20,}$""")
    private val XAI_REGEX = Regex("""^xai-[a-zA-Z0-9_-]{20,}$""")
    private val COHERE_REGEX = Regex("""^(?:co-[a-zA-Z0-9\-]{36,}|TKN-[a-zA-Z0-9\-]{20,}|AUTH_TOKEN=[a-zA-Z0-9\-]{20,})$""")
    private val OPENAI_REGEX = Regex("""^(?:sk-proj-[a-zA-Z0-9\-_]{20,}|sk-ws-[A-Za-z0-9._\-]{30,}|sk-[a-zA-Z0-9\-_]{20,})$""")

    data class ScanSummary(
        val totalKeysFound: Int,
        val keysByPreset: Map<String, List<String>>, // presetId -> list of keys
        val unrecognizedKeys: List<String>,
    )

    fun cleanKey(raw: String): String {
        var k = raw.trim().trim('"', '\'', '`', ',', ';')
        if (k.startsWith("AUTH_TOKEN=")) {
            k = k.substringAfter("AUTH_TOKEN=").trim()
        }
        return k
    }

    /**
     * Determines which basic provider preset a key belongs to.
     * Returns presetId or null if unrecognized.
     * Note: "custom" and "local" are NEVER returned.
     */
    fun detectPresetId(rawKey: String): String? {
        val clean = cleanKey(rawKey)
        if (clean.isEmpty()) return null

        return when {
            ANTHROPIC_REGEX.matches(clean) || clean.startsWith("sk-ant-") -> "anthropic"
            OPENROUTER_REGEX.matches(clean) || clean.startsWith("sk-or-") -> "openrouter"
            GOOGLE_REGEX.matches(clean) || (clean.startsWith("AIza") && clean.length == 39) -> "google"
            GROQ_REGEX.matches(clean) || clean.startsWith("gsk_") -> "groq"
            CEREBRAS_REGEX.matches(clean) || clean.startsWith("csk_") -> "cerebras"
            PERPLEXITY_REGEX.matches(clean) || clean.startsWith("pplx-") || clean.startsWith("pa-") -> "perplexity"
            TOGETHER_REGEX.matches(clean) || clean.startsWith("together_") -> "together"
            FIREWORKS_REGEX.matches(clean) || clean.startsWith("fw_") -> "fireworks"
            XAI_REGEX.matches(clean) || clean.startsWith("xai-") -> "xai"
            COHERE_REGEX.matches(clean) || clean.startsWith("co-") || clean.startsWith("TKN-") -> "cohere"
            OPENAI_REGEX.matches(clean) || clean.startsWith("sk-") -> "openai"
            else -> null
        }
    }

    /**
     * Scans arbitrary text / file content, extracts keys using regexes,
     * categorizes them into basic providers, and returns a structured summary.
     */
    fun scan(content: String): ScanSummary {
        val foundKeys = LinkedHashSet<String>()

        // 1. Scan with master regex
        for (match in COMBINED_KEY_REGEX.findAll(content)) {
            val key = cleanKey(match.value)
            if (key.isNotEmpty()) {
                foundKeys.add(key)
            }
        }

        // 2. Also parse line-by-line / delimiter-based to catch keys in .env, JSON, or lists
        for (key in KeyParser.parse(content)) {
            val cleaned = cleanKey(key)
            if (cleaned.isNotEmpty()) {
                foundKeys.add(cleaned)
            }
        }

        val byPreset = mutableMapOf<String, MutableList<String>>()
        val unrecognized = mutableListOf<String>()

        for (key in foundKeys) {
            val presetId = detectPresetId(key)
            if (presetId != null) {
                val preset = BuiltInPresets.byId(presetId)
                if (preset != null && presetId != "custom" && presetId != Provider.PRESET_LOCAL) {
                    byPreset.getOrPut(presetId) { mutableListOf() }.add(key)
                } else {
                    unrecognized.add(key)
                }
            } else {
                unrecognized.add(key)
            }
        }

        return ScanSummary(
            totalKeysFound = foundKeys.size,
            keysByPreset = byPreset.mapValues { it.value.distinct() },
            unrecognizedKeys = unrecognized.distinct(),
        )
    }
}
