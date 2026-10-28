package com.diaryvault.app.config

import com.diaryvault.app.BuildConfig

/** Supported cloud LLM providers for Mirror. */
enum class AIProvider {
    OPENAI,
    ANTHROPIC,
}

/**
 * Cloud LLM configuration. No secrets belong in the repo.
 *
 * Wire a cloud chat API at build/run time:
 * - DIARY_AI_API_KEY — required to leave mock mode (never commit)
 * - DIARY_AI_PROVIDER — optional; openai (default) or anthropic.
 *   Auto-detects anthropic when DIARY_AI_BASE_URL contains "anthropic".
 * - DIARY_AI_BASE_URL — optional; defaults to OpenAI or Anthropic host by provider
 * - DIARY_AI_MODEL — optional; defaults to gpt-4o-mini (openai) or claude-sonnet-4-5 (anthropic)
 * - DIARY_AI_MAX_TOKENS — optional; max completion tokens (default 400)
 * - DIARY_AI_MAX_CONTEXT_ENTRIES — optional; diary excerpts in prompt (default 8)
 * - DIARY_AI_FREE_DAILY_REQUEST_LIMIT — Free-tier daily Mirror cap (default 8)
 * - DIARY_AI_DAILY_REQUEST_LIMIT — Author Plus daily cap (default 40; 0 = unlimited for Plus)
 *
 * Data residency narrative: Taiwan (TW).
 */
object AIConfig {
    const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com"
    const val DEFAULT_ANTHROPIC_BASE_URL = "https://api.anthropic.com"
    const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
    const val DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-5"
    /** Anthropic Messages API version header value. */
    const val ANTHROPIC_API_VERSION = "2023-06-01"
    const val DEFAULT_MAX_TOKENS = 400
    const val DEFAULT_MAX_CONTEXT_ENTRIES = 8
    const val DEFAULT_FREE_DAILY_REQUEST_LIMIT = 8
    const val DEFAULT_DAILY_REQUEST_LIMIT = 40
    const val MAX_ENTRY_BODY_CHARS = 600
    const val MAX_CONVERSATION_TURNS = 8

    val apiKey: String?
        get() = BuildConfig.DIARY_AI_API_KEY.takeIf { it.isNotBlank() }
            ?: System.getenv("DIARY_AI_API_KEY")?.takeIf { it.isNotBlank() }

    /** Resolved provider: explicit DIARY_AI_PROVIDER, else auto-detect from BASE_URL, else openai. */
    val provider: AIProvider
        get() {
            val raw = BuildConfig.DIARY_AI_PROVIDER.takeIf { it.isNotBlank() }
                ?: System.getenv("DIARY_AI_PROVIDER")?.takeIf { it.isNotBlank() }
            when (raw?.trim()?.lowercase()) {
                "anthropic" -> return AIProvider.ANTHROPIC
                "openai" -> return AIProvider.OPENAI
            }
            val base = BuildConfig.DIARY_AI_BASE_URL.takeIf { it.isNotBlank() }
                ?: System.getenv("DIARY_AI_BASE_URL")?.takeIf { it.isNotBlank() }
            if (base?.contains("anthropic", ignoreCase = true) == true) {
                return AIProvider.ANTHROPIC
            }
            return AIProvider.OPENAI
        }

    val baseUrl: String?
        get() {
            val configured = BuildConfig.DIARY_AI_BASE_URL.takeIf { it.isNotBlank() }
                ?: System.getenv("DIARY_AI_BASE_URL")?.takeIf { it.isNotBlank() }
            if (!configured.isNullOrBlank()) return configured.trimEnd('/')
            if (apiKey == null) return null
            return if (provider == AIProvider.ANTHROPIC) {
                DEFAULT_ANTHROPIC_BASE_URL
            } else {
                DEFAULT_OPENAI_BASE_URL
            }
        }

    val model: String
        get() {
            val explicit = BuildConfig.DIARY_AI_MODEL.takeIf { it.isNotBlank() }
                ?: System.getenv("DIARY_AI_MODEL")?.takeIf { it.isNotBlank() }
            if (!explicit.isNullOrBlank()) return explicit
            return if (provider == AIProvider.ANTHROPIC) {
                DEFAULT_ANTHROPIC_MODEL
            } else {
                DEFAULT_OPENAI_MODEL
            }
        }

    val maxTokens: Int
        get() = intFromBuildOrEnv("DIARY_AI_MAX_TOKENS", BuildConfig.DIARY_AI_MAX_TOKENS, DEFAULT_MAX_TOKENS)

    val maxContextEntries: Int
        get() = intFromBuildOrEnv(
            "DIARY_AI_MAX_CONTEXT_ENTRIES",
            BuildConfig.DIARY_AI_MAX_CONTEXT_ENTRIES,
            DEFAULT_MAX_CONTEXT_ENTRIES,
        )

    /** Free-tier local daily Mirror cap. Always at least 1. */
    val freeDailyRequestLimit: Int
        get() = intFromBuildOrEnv(
            "DIARY_AI_FREE_DAILY_REQUEST_LIMIT",
            BuildConfig.DIARY_AI_FREE_DAILY_REQUEST_LIMIT,
            DEFAULT_FREE_DAILY_REQUEST_LIMIT,
        ).coerceAtLeast(1)

    /** Author Plus local daily Mirror cap. `0` disables the Plus-tier cap. */
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
