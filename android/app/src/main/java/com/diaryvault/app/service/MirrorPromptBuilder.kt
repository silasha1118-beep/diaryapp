package com.diaryvault.app.service

import com.diaryvault.app.config.AIConfig
import com.diaryvault.app.model.DiaryEntry
import com.diaryvault.app.model.MirrorContext
import com.diaryvault.app.model.MirrorRole

/** Builds the mirror-style system prompt and provider message lists (RAG-lite). */
object MirrorPromptBuilder {
    fun systemPrompt(context: MirrorContext, entries: List<DiaryEntry>): String {
        val name = context.userDisplayName.trim()
        val nameLine = if (name.isEmpty()) {
            "The author has not set a display name."
        } else {
            "The author's preferred name is $name."
        }

        val languageInstruction = when {
            context.localeTag.startsWith("zh-Hant", ignoreCase = true) ||
                context.localeTag.startsWith("zh-TW", ignoreCase = true) ||
                context.localeTag.startsWith("zh-HK", ignoreCase = true) ->
                "Reply in Traditional Chinese (繁體中文)."
            context.localeTag.startsWith("zh-Hans", ignoreCase = true) ||
                context.localeTag.startsWith("zh-CN", ignoreCase = true) ->
                "Reply in Simplified Chinese (简体中文)."
            context.localeTag.startsWith("zh", ignoreCase = true) ->
                "Reply in Traditional Chinese (繁體中文)."
            else -> "Reply in English."
        }

        val excerpts = if (entries.isEmpty()) {
            "(No diary excerpts yet. If there is nothing to ground on, say so gently and invite writing — do not invent memories.)"
        } else {
            entries.joinToString("\n\n") { entry ->
                val body = truncate(entry.body, AIConfig.MAX_ENTRY_BODY_CHARS)
                val title = entry.title.ifBlank { "(untitled)" }
                """
                [id: ${entry.id}]
                title: $title
                body: $body
                """.trimIndent()
            }
        }

        return """
            You are Mirror for Diary Vault — a gentle, grounded companion for a living author.
            Product data-residency narrative: ${AIConfig.dataResidencyLabel}. Treat diary text as private; do not suggest exporting it elsewhere.

            $nameLine
            $languageInstruction

            Style (strict):
            - Mirror style only: briefly reflect / restate what the author wrote or just said, then ask 1–2 guiding questions.
            - Never give coach-style prescriptions, action plans, “you should…”, or numbered “do this next” steps.
            - Stay warm, contextual, and restrained. Prefer short paragraphs.
            - Ground claims in the diary excerpts below. When you lean on an excerpt, quote a short phrase and include its [id: …] in the reply.
            - If excerpts are thin or irrelevant, say you don’t have enough from their vault yet — do not invent facts.
            - End with a short disclaimer line: “根據你寫過的內容” / “根据你写过的内容” / “Based on what you’ve written” matching the reply language.

            Diary excerpts (most recent first; RAG-lite — not fine-tuning):
            $excerpts
        """.trimIndent()
    }

    /** OpenAI chat message dictionaries (`role` + `content`), including system. */
    fun chatMessages(userText: String, context: MirrorContext): List<Map<String, String>> {
        val entries = context.recentEntries.take(AIConfig.maxContextEntries)
        val messages = mutableListOf(
            mapOf("role" to "system", "content" to systemPrompt(context, entries)),
        )
        messages += conversationTurns(userText, context)
        messages += mapOf("role" to "user", "content" to userText)
        return messages
    }

    /** Anthropic Messages API shape: top-level system + user/assistant turns only. */
    data class AnthropicPrompt(
        val system: String,
        val messages: List<Map<String, String>>,
    )

    fun anthropicPrompt(userText: String, context: MirrorContext): AnthropicPrompt {
        val entries = context.recentEntries.take(AIConfig.maxContextEntries)
        val messages = conversationTurns(userText, context).toMutableList()
        messages += mapOf("role" to "user", "content" to userText)
        return AnthropicPrompt(system = systemPrompt(context, entries), messages = messages)
    }

    private fun conversationTurns(userText: String, context: MirrorContext): List<Map<String, String>> {
        val prior = context.conversation
            .filter { it.role == MirrorRole.User || it.role == MirrorRole.Mirror }
            .takeLast(AIConfig.MAX_CONVERSATION_TURNS)

        val messages = mutableListOf<Map<String, String>>()
        for (message in prior) {
            if (message.role == MirrorRole.User && message.text == userText) continue
            val role = if (message.role == MirrorRole.User) "user" else "assistant"
            messages += mapOf("role" to role, "content" to message.text)
        }
        return messages
    }

    fun citedIds(text: String, entries: List<DiaryEntry>): List<String> {
        val known = entries.map { it.id }.toSet()
        val regex = Regex(
            "[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}",
        )
        val found = linkedSetOf<String>()
        for (match in regex.findAll(text)) {
            val id = match.value
            if (id in known) found += id
        }
        return found.toList()
    }

    private fun truncate(text: String, limit: Int): String {
        val trimmed = text.trim()
        return if (trimmed.length <= limit) trimmed else trimmed.take(limit) + "…"
    }
}
