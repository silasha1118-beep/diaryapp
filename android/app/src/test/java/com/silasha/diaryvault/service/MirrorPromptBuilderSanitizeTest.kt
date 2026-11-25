package com.silasha.diaryvault.service

import com.silasha.diaryvault.model.DiaryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorPromptBuilderSanitizeTest {
    private val entryId = "62C347FF-6854-4163-A76A-B9306912F0C7"

    @Test
    fun sanitize_stripsIdMarkerAndUuid() {
        val raw = "你寫過「累」[id: $entryId] 的感覺，現在還在嗎？\n\n根據你寫過的內容"
        val cleaned = MirrorPromptBuilder.sanitizeAssistantText(raw)
        assertFalse(cleaned.contains(entryId, ignoreCase = true))
        assertFalse(cleaned.contains("[id:", ignoreCase = true))
        assertFalse(cleaned.contains("根據你寫過的內容"))
        assertTrue(cleaned.contains("累"))
    }

    @Test
    fun finalize_stripsIdsAndReturnsNoCitedBadgeFlag() {
        val entries = listOf(
            DiaryEntry(id = entryId, title = "tired", body = "好累"),
        )
        val raw = "對照你寫的「好累」[id: $entryId]。\n\nBased on what you've written"
        val finalized = MirrorPromptBuilder.finalizeAssistantReply(raw, entries)
        assertTrue(finalized.citedEntryIds.isEmpty())
        assertFalse(finalized.text.contains(entryId, ignoreCase = true))
        assertFalse(finalized.text.contains("Based on what you've written", ignoreCase = true))
    }

    @Test
    fun finalize_neverFallsBackToContextEntriesForBadge() {
        val entries = listOf(
            DiaryEntry(id = entryId, title = "tired", body = "好累"),
            DiaryEntry(id = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE", title = "two", body = "二"),
        )
        val finalized = MirrorPromptBuilder.finalizeAssistantReply(
            "你提到好累——那份沉重現在還在嗎？",
            entries,
        )
        assertTrue(finalized.citedEntryIds.isEmpty())
        assertEquals("你提到好累——那份沉重現在還在嗎？", finalized.text)
    }

    @Test
    fun systemPrompt_matchesVarietyCodeSwitchAndAllowsCasualReplies() {
        val prompt = MirrorPromptBuilder.systemPrompt(
            context = com.silasha.diaryvault.model.MirrorContext(
                userDisplayName = "Silas",
                localeTag = "zh-Hant-HK",
                recentEntries = emptyList(),
                conversation = emptyList(),
            ),
            entries = emptyList(),
        )
        assertTrue(prompt.contains("Language & variety"))
        assertTrue(prompt.contains("Hong Kong Cantonese"))
        assertTrue(prompt.contains("Taiwan Mandarin"))
        assertTrue(prompt.contains("Mainland Mandarin"))
        assertTrue(prompt.contains("Code-switching") || prompt.contains("中英夾雜"))
        assertTrue(prompt.contains("Never force the app UI locale"))
        assertTrue(prompt.contains("Never print entry IDs"))
        assertTrue(prompt.contains("Do not end with attribution closers"))
        assertTrue(prompt.contains("Do not force a therapy paraphrase"))
        assertFalse(prompt.contains("Reply in Traditional Chinese"))
        assertFalse(prompt.contains("End with a short disclaimer line"))
        assertFalse(prompt.contains("include its [id:"))
        assertFalse(prompt.contains("The app UI already shows"))
    }
}
