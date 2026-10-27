package com.diaryvault.app.config

import com.diaryvault.app.BuildConfig

/**
 * Cloud LLM configuration stub. No secrets belong in the repo.
 *
 * Wire a real provider at build/run time:
 * - DIARY_AI_BASE_URL
 * - DIARY_AI_API_KEY
 * - DIARY_AI_MODEL (optional)
 *
 * Data residency narrative: Taiwan (TW).
 */
object AIConfig {
    val baseUrl: String?
        get() = BuildConfig.DIARY_AI_BASE_URL.takeIf { it.isNotBlank() }
            ?: System.getenv("DIARY_AI_BASE_URL")?.takeIf { it.isNotBlank() }

    val apiKey: String?
        get() = BuildConfig.DIARY_AI_API_KEY.takeIf { it.isNotBlank() }
            ?: System.getenv("DIARY_AI_API_KEY")?.takeIf { it.isNotBlank() }

    val model: String
        get() = BuildConfig.DIARY_AI_MODEL.takeIf { it.isNotBlank() }
            ?: System.getenv("DIARY_AI_MODEL")?.takeIf { it.isNotBlank() }
            ?: "mirror-v0"

    val useCloudClient: Boolean
        get() = !baseUrl.isNullOrBlank() && !apiKey.isNullOrBlank()

    const val dataResidencyLabel: String = "Taiwan (TW)"
}
