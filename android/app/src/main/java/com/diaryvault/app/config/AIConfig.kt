package com.diaryvault.app.config

import com.diaryvault.app.BuildConfig

/**
 * Cloud LLM configuration. No secrets belong in the repo.
 *
 * Wire an OpenAI-compatible chat API at build/run time:
 * - DIARY_AI_API_KEY — required to leave mock mode (never commit)
 * - DIARY_AI_BASE_URL — optional; defaults to https://api.openai.com
 * - DIARY_AI_MODEL — optional; defaults to gpt-4o-mini
 * - DIARY_AI_MAX_TOKENS — optional; max completion tokens (default 400)
 * - DIARY_AI_MAX_CONTEXT_ENTRIES — optional; diary excerpts in prompt (default 8)
 * - DIARY_AI_DAILY_REQUEST_LIMIT — optional; local daily cap stub (default 40; 0 = off)
 *
 * Data residency narrative: Taiwan (TW).
 */
object AIConfig {
    const val DEFAULT_BASE_URL = "https://api.openai.com"
    const val DEFAULT_MODEL = "gpt-4o-mini"
    const val DEFAULT_MAX_TOKENS = 400
    const val DEFAULT_MAX_CONTEXT_ENTRIES = 8
    const val DEFAULT_DAILY_REQUEST_LIMIT = 40
    const val MAX_ENTRY_BODY_CHARS = 600
    const val MAX_CONVERSATION_TURNS = 8

    val apiKey: String?
        get() = BuildConfig.DIARY_AI_API_KEY.takeIf { it.isNotBlank() }
            ?: System.getenv("DIARY_AI_API_KEY")?.takeIf { it.isNotBlank() }

    val baseUrl: String?
        get() {
            val configured = BuildConfig.DIARY_AI_BASE_URL.takeIf { it.isNotBlank() }
                ?: System.getenv("DIARY_AI_BASE_URL")?.takeIf { it.isNotBlank() }
            if (!configured.isNullOrBlank()) return configured.trimEnd('/')
            return if (apiKey != null) DEFAULT_BASE_URL else null
        }

    val model: String
        get() = BuildConfig.DIARY_AI_MODEL.takeIf { it.isNotBlank() }
            ?: System.getenv("DIARY_AI_MODEL")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_MODEL

    val maxTokens: Int
        get() = intFromBuildOrEnv("DIARY_AI_MAX_TOKENS", BuildConfig.DIARY_AI_MAX_TOKENS, DEFAULT_MAX_TOKENS)

    val maxContextEntries: Int
        get() = intFromBuildOrEnv(
            "DIARY_AI_MAX_CONTEXT_ENTRIES",
            BuildConfig.DIARY_AI_MAX_CONTEXT_ENTRIES,
            DEFAULT_MAX_CONTEXT_ENTRIES,
        )

    /** Local stub counter. `0` disables the cap. */
    val dailyRequestLimit: Int
        get() = intFromBuildOrEnv(
            "DIARY_AI_DAILY_REQUEST_LIMIT",
            BuildConfig.DIARY_AI_DAILY_REQUEST_LIMIT,
            DEFAULT_DAILY_REQUEST_LIMIT,
        )

    val useCloudClient: Boolean
        get() = !apiKey.isNullOrBlank()

    const val dataResidencyLabel: String = "Taiwan (TW)"

    private fun intFromBuildOrEnv(envKey: String, buildValue: String, default: Int): Int {
        val raw = buildValue.takeIf { it.isNotBlank() }
            ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }
        return raw?.toIntOrNull()?.coerceAtLeast(0) ?: default
    }
}
