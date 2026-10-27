package com.diaryvault.app.service

import com.diaryvault.app.BuildConfig
import com.diaryvault.app.R
import com.diaryvault.app.config.AIConfig
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
    suspend fun reply(userText: String, context: MirrorContext, stringLookup: (Int, Array<out Any>?) -> String): MirrorMessage
}

sealed class MirrorAIError(message: String) : Exception(message) {
    data object EmptyInput : MirrorAIError("empty")
    data object NotConfigured : MirrorAIError("not configured")
    data object Decoding : MirrorAIError("decoding")
    class Network(detail: String) : MirrorAIError(detail)
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
 * Stub cloud client. Activated only when [AIConfig.useCloudClient] is true.
 * Does not embed secrets; reads DIARY_AI_* from BuildConfig / process env.
 */
class CloudMirrorAIClient : MirrorAIService {
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

        val endpoint = baseUrl.trimEnd('/') + "/v1/mirror"
        val payload = mapOf(
            "model" to AIConfig.model,
            "locale" to context.localeTag,
            "dataResidency" to AIConfig.dataResidencyLabel,
            "style" to "mirror",
            "message" to trimmed,
            "displayName" to context.userDisplayName,
            "entries" to context.recentEntries.take(12).map { entry ->
                mapOf(
                    "id" to entry.id,
                    "title" to entry.title,
                    "body" to entry.body,
                    "createdAt" to entry.createdAt,
                )
            },
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
            if (code !in 200..299) {
                throw MirrorAIError.Network("Cloud AI HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().readText()
            val json = gson.fromJson(body, JsonObject::class.java)
            val text = json.get("text")?.asString ?: throw MirrorAIError.Decoding
            val cited = json.getAsJsonArray("citedEntryIds")
                ?.mapNotNull { it.asString }
                ?: emptyList()
            return MirrorMessage(role = MirrorRole.Mirror, text = text, citedEntryIds = cited)
        } finally {
            connection.disconnect()
        }
    }
}

fun createMirrorAIService(): MirrorAIService =
    if (AIConfig.useCloudClient) CloudMirrorAIClient() else MockMirrorAIClient()

/** Suppress unused BuildConfig warning when cloud keys are empty at compile time. */
@Suppress("unused")
private fun touchBuildConfig() = BuildConfig.BUILD_TYPE

fun localeForLanguageTag(tag: String): Locale =
    when {
        tag.startsWith("zh-Hant", ignoreCase = true) -> Locale.TRADITIONAL_CHINESE
        tag.startsWith("zh-Hans", ignoreCase = true) -> Locale.SIMPLIFIED_CHINESE
        else -> Locale.ENGLISH
    }
