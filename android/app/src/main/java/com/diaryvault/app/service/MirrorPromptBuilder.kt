package com.diaryvault.app.service

import com.diaryvault.app.config.AIConfig
import com.diaryvault.app.model.DiaryEntry
import com.diaryvault.app.model.MirrorContext
import com.diaryvault.app.model.MirrorRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Builds the mirror-style system prompt and provider message lists (RAG-lite). */
object MirrorPromptBuilder {
    private val uuidRegex = Regex(
        "[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}",
    )
    private val idMarkerRegex = Regex(
        "\\[id:\\s*[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}\\]",
        RegexOption.IGNORE_CASE,
    )

    /** Boilerplate attribution closings / caption echoes — strip from the reply body. */
    private val attributionBoilerplatePatterns = listOf(
        Regex("""^[\s—–\-·•]*根據你寫過的內容.*$"""),
        Regex("""^[\s—–\-·•]*根据你写过的内容.*$"""),
        Regex("""^[\s—–\-·•]*Based on what you(?:'|’)?ve written.*$""", RegexOption.IGNORE_CASE),
        Regex("""^[\s—–\-·•]*Grounded in your entries\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^[\s—–\-·•]*依據你的日記\.?$"""),
        Regex("""^[\s—–\-·•]*依据你的日记\.?$"""),
        Regex("""^[\s—–\-·•]*書いた内容に基づく.*$"""),
        Regex("""^[\s—–\-·•]*อิงจากสิ่งที่คุณเขียน.*$"""),
    )

    private val excerptDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    fun systemPrompt(context: MirrorContext, entries: List<DiaryEntry>): String {
        val name = context.userDisplayName.trim()
        val nameLine = if (name.isEmpty()) {
            "The author has not set a display name."
        } else {
            "The author's preferred name is $name."
        }

        // Variety + register follow the latest user message(s) — not the app UI locale.
        val languageInstruction = """
            Language & variety (strict — like a fluent local chat partner, not a locale switch):
            - Detect language, variety, and style from the author's latest message(s) (and recent turns if needed). Reply in that same variety. Never force the app UI locale.
            - Chinese varieties (do not collapse them):
              · Hong Kong Cantonese / 粵語／港式書面語 (e.g. 係、唔、嚟、咗、喺、嘅、睇、仲、冇、點解、諗) → reply in natural HK Cantonese / 港式書面語.
              · Taiwan Mandarin / 台灣華語 (Traditional; 台灣用字語氣) → reply in Taiwan Mandarin.
              · Mainland Mandarin / 简体中文 → reply in Simplified Mainland Mandarin.
              · If Traditional Chinese without clear HK vs TW cues, prefer the closer match from recent turns; still do not invent a different variety.
            - Code-switching / 中英夾雜: if they mix Chinese and English in one message, reply the same way — natural bilingual like a Hong Konger (or matching their mix). Do not force pure Chinese or pure English.
            - English, Japanese, Thai, etc.: match that language and their register (casual vs careful).
            - Mirror their tone: short/casual → short/casual; reflective → reflective. Stay Mirror voice (reflect/ask, never prescribe).
        """.trimIndent()

        val excerpts = if (entries.isEmpty()) {
            "(No diary excerpts yet. If there is nothing to ground on, say so gently and invite writing — do not invent memories.)"
        } else {
            entries.joinToString("\n\n") { entry ->
                val body = truncate(entry.body, AIConfig.MAX_ENTRY_BODY_CHARS)
                val title = entry.title.ifBlank { "(untitled)" }
                val date = excerptDateFormat.format(Date(entry.createdAt))
                """
                [id: ${entry.id}]
                date: $date
                title: $title
                body: $body
                """.trimIndent()
            }
        }

        return """
            You are Mirror for Diary Vault — a reflective companion for a living author.
            Product data-residency narrative: ${AIConfig.dataResidencyLabel}. Treat diary text as private; do not suggest exporting it elsewhere.

            $nameLine
            $languageInstruction

            Voice:
            - Reflective mirror, not an advice-giver, coach, or therapist script.
            - Never give prescriptions, action plans, “you should…”, or numbered “do this next” steps.
            - Stay warm and natural. Prefer short paragraphs when a short answer fits.

            How to answer:
            - For casual / meta questions (e.g. “what are you doing?”, small talk, how Mirror works), answer plainly and conversationally. Do not force a therapy paraphrase or a guiding-question loop.
            - When the author is processing feelings or something in their vault is clearly relevant, you may briefly reflect and optionally ask 1 thoughtful question — only when it helps.
            - Diary excerpts below are optional context. Ground in them when relevant (natural language only: date, title, or a short quoted phrase). Do not drag diary into every reply.
            - Never print entry IDs, UUIDs, or `[id: …]` markers. Those markers are for your private grounding only.
            - If excerpts are thin or irrelevant, do not invent facts.
            - Do not end with attribution closers such as “根據你寫過的內容”, “Grounded in your entries”, or “Based on what you’ve written”. Keep the reply body natural chat only.

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
        val found = linkedSetOf<String>()
        for (match in uuidRegex.findAll(text)) {
            val id = match.value
            if (id in known) found += id
        }
        return found.toList()
    }

    /** Defensive clean-up: strip leaked `[id:…]` / UUID markers and forced attribution closings. */
    fun sanitizeAssistantText(text: String): String {
        var result = idMarkerRegex.replace(text, "")
        result = uuidRegex.replace(result, "")

        val lines = result.split('\n')
        val kept = lines.filter { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@filter true
            attributionBoilerplatePatterns.none { it.matches(trimmed) }
        }

        val collapsed = mutableListOf<String>()
        var pendingBlank = false
        for (line in kept) {
            if (line.trim().isEmpty()) {
                pendingBlank = collapsed.isNotEmpty()
                continue
            }
            if (pendingBlank) {
                collapsed += ""
                pendingBlank = false
            }
            collapsed += line.replace(Regex("""\s{2,}"""), " ").trim()
        }
        return collapsed.joinToString("\n").trim()
    }

    /**
     * Sanitize display text. Citation badge UI is removed — always return empty
     * [FinalizedReply.citedEntryIds] so persisted/old clients cannot re-show a
     * grounded caption from a leftover flag.
     */
    data class FinalizedReply(
        val text: String,
        val citedEntryIds: List<String>,
    )

    fun finalizeAssistantReply(rawText: String, contextEntries: List<DiaryEntry>): FinalizedReply {
        // Still parse then discard — keeps sanitize path free of leaked IDs in the body.
        citedIds(rawText, contextEntries)
        val cleaned = sanitizeAssistantText(rawText)
        return FinalizedReply(text = cleaned, citedEntryIds = emptyList())
    }

    private fun truncate(text: String, limit: Int): String {
        val trimmed = text.trim()
        return if (trimmed.length <= limit) trimmed else trimmed.take(limit) + "…"
    }
}
