package com.diaryvault.app.service

import android.content.Context
import com.diaryvault.app.R
import com.diaryvault.app.config.AIConfig
import com.diaryvault.app.config.AIProvider
import com.diaryvault.app.model.MirrorContext
import com.diaryvault.app.model.MirrorMessage
import com.diaryvault.app.model.MirrorRole
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

interface MirrorAIService {
    suspend fun reply(
        userText: String,
        context: MirrorContext,
        stringLookup: (Int, Array<out Any>?) -> String,
    ): MirrorMessage
}

sealed class MirrorAIError(message: String) : Exception(message) {
    data object EmptyInput : MirrorAIError("empty")
    data object NotConfigured : MirrorAIError("not configured")
    data object Decoding : MirrorAIError("decoding")
    /** Opaque network failure — UI must not show [detail] (may be empty). */
    class Network(detail: String = "") : MirrorAIError(detail)
    /** Provider capacity / rate-limit / resource-exhausted (xAI etc.). */
    data object ProviderBusy : MirrorAIError("provider busy")
    class RateLimited(
        val limit: Int,
        val isAuthorPlus: Boolean = false,
        val isAuthorPro: Boolean = false,
    ) : MirrorAIError(
        when {
            isAuthorPro ->
                "Daily Author Pro Mirror limit reached ($limit). Try again tomorrow."
            isAuthorPlus ->
                "Daily Author Plus Mirror limit reached ($limit). Upgrade to Author Pro or try again tomorrow."
            else ->
                "Free Mirror lifetime limit reached ($limit). Upgrade to Author Plus to continue."
        },
    )

    companion object {
        /** Maps a non-2xx Cloud AI response without exposing raw JSON to the UI. */
        fun fromHttp(statusCode: Int, body: String?): MirrorAIError {
            val text = body.orEmpty()
            return if (isProviderBusy(statusCode, text)) ProviderBusy
            else Network("HTTP $statusCode")
        }

        fun isProviderBusy(statusCode: Int, body: String): Boolean {
            if (statusCode == 429 || statusCode == 503) return true
            val lowered = body.lowercase(Locale.US)
            val markers = listOf(
                "resource-exhausted",
                "resource_exhausted",
                "resourceexhausted",
                "rate_limit",
                "rate-limit",
                "ratelimit",
                "too many requests",
                "capacity",
                "overloaded",
                "quota exceeded",
            )
            return markers.any { lowered.contains(it) }
        }
    }
}

/**
 * Local placeholder for offline demos / tests only.
 * Production Mirror uses [CloudMirrorAIClient] (xAI Grok) when a key is present.
 */
class MockMirrorAIClient : MirrorAIService {
    override suspend fun reply(
        userText: String,
        context: MirrorContext,
        stringLookup: (Int, Array<out Any>?) -> String,
    ): MirrorMessage {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) throw MirrorAIError.EmptyInput

        delay(450)

        val snippet = context.recentEntries.firstOrNull()?.preview?.takeIf { it.isNotBlank() }

        val reflection = if (snippet != null) {
            stringLookup(R.string.mirror_mock_with_cite, arrayOf(snippet))
        } else {
            stringLookup(R.string.mirror_mock_no_cite, null)
        }
        val question = stringLookup(R.string.mirror_mock_question, null)
        // Citation badge removed — no citedEntryIds / under-bubble caption.
        val text = listOf(reflection, "", question).joinToString("\n")

        return MirrorMessage(role = MirrorRole.Mirror, text = text, citedEntryIds = emptyList())
    }
}

/**
 * OpenAI-compatible chat completions client (`POST /v1/chat/completions`).
 * Working provider: **xAI Grok** (`https://api.x.ai` + `grok-4.7`).
 * Activated when [AIConfig.useCloudClient] is true and provider is openai/xai.
 * Does not embed secrets; reads DIARY_AI_* / XAI_API_KEY from BuildConfig / local.properties / env.
 */
class CloudMirrorAIClient(
    private val appContext: Context,
    private val isAuthorPlus: () -> Boolean = { false },
    private val isAuthorPro: () -> Boolean = { false },
) : MirrorAIService {
    private val gson = Gson()

    override suspend fun reply(
        userText: String,
        context: MirrorContext,
        stringLookup: (Int, Array<out Any>?) -> String,
    ): MirrorMessage {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) throw MirrorAIError.EmptyInput

        val baseUrl = AIConfig.baseUrl ?: throw MirrorAIError.NotConfigured
        val apiKey = AIConfig.apiKey ?: throw MirrorAIError.NotConfigured

        MirrorUsageGuard.checkAndIncrement(
            appContext,
            isAuthorPlus = isAuthorPlus(),
            isAuthorPro = isAuthorPro(),
        )

        val endpoint = baseUrl.trimEnd('/') + "/v1/chat/completions"
        val payload = mapOf(
            "model" to AIConfig.model,
            "max_tokens" to AIConfig.maxTokens,
            "temperature" to 0.7,
            "messages" to MirrorPromptBuilder.chatMessages(trimmed, context),
        )

        val connection = try {
            (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 90_000
            }
        } catch (_: IOException) {
            throw MirrorAIError.Network()
        }

        try {
            connection.outputStream.use { it.write(gson.toJson(payload).toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val responseBody = if (code in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            if (code !in 200..299) {
                throw MirrorAIError.fromHttp(code, responseBody)
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val text = json
                .getAsJsonArray("choices")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")
                ?.asString
                ?.trim()
                ?: throw MirrorAIError.Decoding
            if (text.isEmpty()) throw MirrorAIError.Decoding

            val contextEntries = context.recentEntries.take(AIConfig.maxContextEntries)
            val finalized = MirrorPromptBuilder.finalizeAssistantReply(text, contextEntries)
            if (finalized.text.isEmpty()) throw MirrorAIError.Decoding
            return MirrorMessage(
                role = MirrorRole.Mirror,
                text = finalized.text,
                citedEntryIds = finalized.citedEntryIds,
            )
        } catch (e: MirrorAIError) {
            throw e
        } catch (_: IOException) {
            throw MirrorAIError.Network()
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Anthropic Messages API client (`POST /v1/messages`).
 * Headers: `x-api-key`, `anthropic-version`. Does not embed secrets.
 * Kept for parity with iOS; product working provider is xAI Grok (openai path).
 */
class AnthropicMirrorAIClient(
    private val appContext: Context,
    private val isAuthorPlus: () -> Boolean = { false },
    private val isAuthorPro: () -> Boolean = { false },
) : MirrorAIService {
    private val gson = Gson()

    override suspend fun reply(
        userText: String,
        context: MirrorContext,
        stringLookup: (Int, Array<out Any>?) -> String,
    ): MirrorMessage {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) throw MirrorAIError.EmptyInput

        val baseUrl = AIConfig.baseUrl ?: throw MirrorAIError.NotConfigured
        val apiKey = AIConfig.apiKey ?: throw MirrorAIError.NotConfigured

        MirrorUsageGuard.checkAndIncrement(
            appContext,
            isAuthorPlus = isAuthorPlus(),
            isAuthorPro = isAuthorPro(),
        )

        val endpoint = baseUrl.trimEnd('/') + "/v1/messages"
        val prompt = MirrorPromptBuilder.anthropicPrompt(trimmed, context)
        val payload = mapOf(
            "model" to AIConfig.model,
            "max_tokens" to AIConfig.maxTokens,
            "temperature" to 0.7,
            "system" to prompt.system,
            "messages" to prompt.messages,
        )

        val connection = try {
            (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", AIConfig.ANTHROPIC_API_VERSION)
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 90_000
            }
        } catch (_: IOException) {
            throw MirrorAIError.Network()
        }

        try {
            connection.outputStream.use { it.write(gson.toJson(payload).toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val responseBody = if (code in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            if (code !in 200..299) {
                throw MirrorAIError.fromHttp(code, responseBody)
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val content = json.getAsJsonArray("content") ?: throw MirrorAIError.Decoding
            val text = content
                .mapNotNull { el ->
                    val obj = el.asJsonObject
                    if (obj.get("type")?.asString == "text") obj.get("text")?.asString else null
                }
                .joinToString("\n")
                .trim()
            if (text.isEmpty()) throw MirrorAIError.Decoding

            val contextEntries = context.recentEntries.take(AIConfig.maxContextEntries)
            val finalized = MirrorPromptBuilder.finalizeAssistantReply(text, contextEntries)
            if (finalized.text.isEmpty()) throw MirrorAIError.Decoding
            return MirrorMessage(
                role = MirrorRole.Mirror,
                text = finalized.text,
                citedEntryIds = finalized.citedEntryIds,
            )
        } catch (e: MirrorAIError) {
            throw e
        } catch (_: IOException) {
            throw MirrorAIError.Network()
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Live cloud Mirror when [AIConfig.useCloudClient]; otherwise [MockMirrorAIClient] for offline demos.
 * Default cloud path is xAI Grok (OpenAI-compatible).
 */
fun createMirrorAIService(
    context: Context,
    isAuthorPlus: () -> Boolean = { false },
    isAuthorPro: () -> Boolean = { false },
): MirrorAIService =
    if (!AIConfig.useCloudClient) {
        MockMirrorAIClient()
    } else {
        when (AIConfig.provider) {
            AIProvider.ANTHROPIC -> AnthropicMirrorAIClient(
                context.applicationContext,
                isAuthorPlus = isAuthorPlus,
                isAuthorPro = isAuthorPro,
            )
            AIProvider.OPENAI -> CloudMirrorAIClient(
                context.applicationContext,
                isAuthorPlus = isAuthorPlus,
                isAuthorPro = isAuthorPro,
            )
        }
    }

fun localeForLanguageTag(tag: String): Locale =
    when {
        tag.startsWith("zh-Hant", ignoreCase = true) -> Locale.TRADITIONAL_CHINESE
        tag.startsWith("zh-Hans", ignoreCase = true) -> Locale.SIMPLIFIED_CHINESE
        tag.startsWith("ja", ignoreCase = true) -> Locale.JAPANESE
        tag.startsWith("th", ignoreCase = true) -> Locale.forLanguageTag("th")
        else -> Locale.ENGLISH
    }
