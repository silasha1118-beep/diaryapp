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
    class Network(detail: String) : MirrorAIError(detail)
    class RateLimited(limit: Int) : MirrorAIError(
        "Daily Mirror request limit reached ($limit). Try again tomorrow, raise DIARY_AI_DAILY_REQUEST_LIMIT, or unset the key to use mock.",
    )
}

/** Local placeholder that demonstrates mirror tone without calling a network API. */
class MockMirrorAIClient : MirrorAIService {
    override suspend fun reply(
        userText: String,
        context: MirrorContext,
        stringLookup: (Int, Array<out Any>?) -> String,
    ): MirrorMessage {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) throw MirrorAIError.EmptyInput

        delay(450)

        val cited = context.recentEntries.take(2).map { it.id }
        val snippet = context.recentEntries.firstOrNull()?.preview?.takeIf { it.isNotBlank() }

        val reflection = if (snippet != null) {
            stringLookup(R.string.mirror_mock_with_cite, arrayOf(snippet))
        } else {
            stringLookup(R.string.mirror_mock_no_cite, null)
        }
        val question = stringLookup(R.string.mirror_mock_question, null)
        val disclaimer = stringLookup(R.string.mirror_mock_disclaimer, null)
        val text = listOf(reflection, "", question, "", "— $disclaimer").joinToString("\n")

        return MirrorMessage(role = MirrorRole.Mirror, text = text, citedEntryIds = cited)
    }
}

/**
 * OpenAI-compatible chat completions client (`POST /v1/chat/completions`).
 * Activated when [AIConfig.useCloudClient] is true and provider is openai
 * (Groq, DeepSeek, Azure OpenAI chat path, etc.).
 * Does not embed secrets; reads DIARY_AI_* from BuildConfig / process env.
 */
class CloudMirrorAIClient(
    private val appContext: Context,
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

        MirrorUsageGuard.checkAndIncrement(appContext)

        val endpoint = baseUrl.trimEnd('/') + "/v1/chat/completions"
        val payload = mapOf(
            "model" to AIConfig.model,
            "max_tokens" to AIConfig.maxTokens,
            "temperature" to 0.7,
            "messages" to MirrorPromptBuilder.chatMessages(trimmed, context),
        )

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
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
                val snippet = responseBody.trim().take(180).ifBlank { "HTTP $code" }
                throw MirrorAIError.Network("Cloud AI $snippet")
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
            val cited = MirrorPromptBuilder.citedIds(text, contextEntries)
            return MirrorMessage(role = MirrorRole.Mirror, text = text, citedEntryIds = cited)
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Anthropic Messages API client (`POST /v1/messages`).
 * Headers: `x-api-key`, `anthropic-version`. Does not embed secrets.
 */
class AnthropicMirrorAIClient(
    private val appContext: Context,
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

        MirrorUsageGuard.checkAndIncrement(appContext)

        val endpoint = baseUrl.trimEnd('/') + "/v1/messages"
        val prompt = MirrorPromptBuilder.anthropicPrompt(trimmed, context)
        val payload = mapOf(
            "model" to AIConfig.model,
            "max_tokens" to AIConfig.maxTokens,
            "temperature" to 0.7,
            "system" to prompt.system,
            "messages" to prompt.messages,
        )

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", AIConfig.ANTHROPIC_API_VERSION)
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
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
                val snippet = responseBody.trim().take(180).ifBlank { "HTTP $code" }
                throw MirrorAIError.Network("Cloud AI $snippet")
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
            val cited = MirrorPromptBuilder.citedIds(text, contextEntries)
            return MirrorMessage(role = MirrorRole.Mirror, text = text, citedEntryIds = cited)
        } finally {
            connection.disconnect()
        }
    }
}

fun createMirrorAIService(context: Context): MirrorAIService =
    if (!AIConfig.useCloudClient) {
        MockMirrorAIClient()
    } else {
        when (AIConfig.provider) {
            AIProvider.ANTHROPIC -> AnthropicMirrorAIClient(context.applicationContext)
            AIProvider.OPENAI -> CloudMirrorAIClient(context.applicationContext)
        }
    }

fun localeForLanguageTag(tag: String): Locale =
    when {
        tag.startsWith("zh-Hant", ignoreCase = true) -> Locale.TRADITIONAL_CHINESE
        tag.startsWith("zh-Hans", ignoreCase = true) -> Locale.SIMPLIFIED_CHINESE
        else -> Locale.ENGLISH
    }
