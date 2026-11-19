package com.silasha.diaryvault.config

import com.silasha.diaryvault.BuildConfig

/** Supported cloud LLM providers for Mirror. */
enum class AIProvider {
    /** OpenAI-compatible chat completions (xAI Grok, OpenAI, DeepSeek, etc.). */
    OPENAI,
    ANTHROPIC,
}

/**
 * Cloud LLM configuration. No secrets belong in the repo.
 *
 * Wire a cloud chat API at build/run time (local.properties preferred on Windows):
 * - DIARY_AI_API_KEY or XAI_API_KEY — required for live Mirror (never commit)
 * - DIARY_AI_PROVIDER — optional; `openai` / `xai` (default) or `anthropic`.
 *   Auto-detects anthropic when DIARY_AI_BASE_URL contains "anthropic".
 * - DIARY_AI_BASE_URL — optional; default **https://api.x.ai** (app appends `/v1/chat/completions`)
 * - DIARY_AI_MODEL — optional; default **grok-4.7** (openai/xai) or claude-sonnet-4-5 (anthropic)
 * - DIARY_AI_MAX_TOKENS — optional; max completion tokens (default 400)
 * - DIARY_AI_MAX_CONTEXT_ENTRIES — optional; diary excerpts in prompt (default 8)
 * - DIARY_AI_FREE_LIFETIME_REQUEST_LIMIT — Free Mirror lifetime hard cap (default 40; alias FREE_DAILY)
 * - DIARY_AI_DAILY_REQUEST_LIMIT — Author Plus daily Mirror cap (default 8; 0 = unlimited for Plus)
 * - DIARY_AI_PRO_DAILY_REQUEST_LIMIT — Author Pro daily Mirror cap (default 16; 0 = unlimited for Pro)
 *
 * Soft Free Mirror ladders removed (2026-11-01). Mirror quotas stay independent of diary upload quotas
 * ([UploadQuotaConfig] / [com.silasha.diaryvault.service.DiaryUploadUsageGuard]).
 *
 * Working provider (product pref): **xAI Grok** (OpenAI-compatible). No second provider invented.
 * There is no Firebase Functions Mirror proxy in-repo yet — client key via local.properties only.
 *
 * Data residency narrative: Taiwan (TW).
 */
object AIConfig {
    /** Host only — [CloudMirrorAIClient] appends `/v1/chat/completions`. */
    const val DEFAULT_OPENAI_BASE_URL = "https://api.x.ai"
    const val DEFAULT_ANTHROPIC_BASE_URL = "https://api.anthropic.com"
    const val DEFAULT_OPENAI_MODEL = "grok-4.7"
    const val DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-5"
    /** Anthropic Messages API version header value. */
    const val ANTHROPIC_API_VERSION = "2023-06-01"
    const val DEFAULT_MAX_TOKENS = 400
    const val DEFAULT_MAX_CONTEXT_ENTRIES = 8
    const val DEFAULT_FREE_LIFETIME_REQUEST_LIMIT = 40
    const val DEFAULT_DAILY_REQUEST_LIMIT = 8
    const val DEFAULT_PRO_DAILY_REQUEST_LIMIT = 16
    const val MAX_ENTRY_BODY_CHARS = 600
    const val MAX_CONVERSATION_TURNS = 8

    val apiKey: String?
        get() = BuildConfig.DIARY_AI_API_KEY.takeIf { it.isNotBlank() }
            ?: System.getenv("DIARY_AI_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getenv("XAI_API_KEY")?.takeIf { it.isNotBlank() }

    /** Resolved provider: explicit DIARY_AI_PROVIDER, else auto-detect from BASE_URL, else openai (xAI). */
    val provider: AIProvider
        get() {
            val raw = BuildConfig.DIARY_AI_PROVIDER.takeIf { it.isNotBlank() }
                ?: System.getenv("DIARY_AI_PROVIDER")?.takeIf { it.isNotBlank() }
            when (raw?.trim()?.lowercase()) {
                "anthropic" -> return AIProvider.ANTHROPIC
                "openai", "xai", "grok" -> return AIProvider.OPENAI
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
            if (!configured.isNullOrBlank()) {
                // OpenAI SDK style `https://api.x.ai/v1` → strip trailing /v1 (we append it).
                return configured.trimEnd('/').removeSuffix("/v1")
            }
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

    /** Free-tier Mirror lifetime hard cap. Always at least 1. */
    val freeLifetimeRequestLimit: Int
        get() {
            val fromBuild = BuildConfig.DIARY_AI_FREE_LIFETIME_REQUEST_LIMIT.takeIf { it.isNotBlank() }
            if (fromBuild != null) {
                return fromBuild.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_FREE_LIFETIME_REQUEST_LIMIT
            }
            val lifetime = System.getenv("DIARY_AI_FREE_LIFETIME_REQUEST_LIMIT")?.takeIf { it.isNotBlank() }
            if (lifetime != null) {
                return lifetime.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_FREE_LIFETIME_REQUEST_LIMIT
            }
            return intFromBuildOrEnv(
                "DIARY_AI_FREE_DAILY_REQUEST_LIMIT",
                BuildConfig.DIARY_AI_FREE_DAILY_REQUEST_LIMIT,
                DEFAULT_FREE_LIFETIME_REQUEST_LIMIT,
            ).coerceAtLeast(1)
        }

    /** Legacy alias — Free is lifetime, not daily. */
    val freeDailyRequestLimit: Int get() = freeLifetimeRequestLimit

    /** Soft ladders removed — always 0. */
    val freeSoftPaywallThreshold: Int get() = 0

    /** Author Plus local daily Mirror cap. `0` disables the Plus-tier cap. */
    val dailyRequestLimit: Int
        get() = intFromBuildOrEnv(
            "DIARY_AI_DAILY_REQUEST_LIMIT",
            BuildConfig.DIARY_AI_DAILY_REQUEST_LIMIT,
            DEFAULT_DAILY_REQUEST_LIMIT,
        )

    /** Author Pro local daily Mirror cap. `0` disables the Pro-tier cap. */
    val proDailyRequestLimit: Int
        get() = intFromBuildOrEnv(
            "DIARY_AI_PRO_DAILY_REQUEST_LIMIT",
            BuildConfig.DIARY_AI_PRO_DAILY_REQUEST_LIMIT,
            DEFAULT_PRO_DAILY_REQUEST_LIMIT,
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
