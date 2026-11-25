package com.silasha.diaryvault.data

import com.silasha.diaryvault.model.DiaryEntry
import com.silasha.diaryvault.model.DiaryMediaAttachment
import com.silasha.diaryvault.model.DiaryMediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultJsonTransferTest {

    @Test
    fun exportRoundTrip_preservesFields_andUsesIsoDates() {
        val entry = DiaryEntry(
            id = "11111111-1111-1111-1111-111111111111",
            title = "Hello",
            body = "Body text",
            moodTag = "平靜",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_100_000L,
            deleted = false,
            sourcePlatform = "android",
        )
        val json = VaultJsonTransfer.exportJSONString(listOf(entry))
        assertTrue(json.contains("\"createdAt\": \"2023-11-14T22:13:20Z\"") || json.contains("2023-11-14T22:13:20Z"))
        assertTrue(json.contains("\"id\": \"11111111-1111-1111-1111-111111111111\""))

        val parsed = VaultJsonTransfer.parseImport(json.toByteArray(Charsets.UTF_8))
        assertEquals(1, parsed.entries.size)
        assertEquals(0, parsed.mediaStripped)
        val round = parsed.entries.first()
        assertEquals(entry.id, round.id)
        assertEquals(entry.title, round.title)
        assertEquals(entry.body, round.body)
        assertEquals(entry.moodTag, round.moodTag)
        assertEquals(entry.createdAt, round.createdAt)
        assertEquals(entry.updatedAt, round.updatedAt)
        assertEquals("android", round.sourcePlatform)
        assertTrue(round.attachments.isEmpty())
    }

    @Test
    fun import_iosShapedJson_stripsMediaMetadata() {
        val iosJson = """
            [
              {
                "attachments" : [
                  {
                    "createdAt" : "2024-01-02T03:04:05Z",
                    "id" : "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "kind" : "voice",
                    "localFileName" : "note.m4a",
                    "mimeType" : "audio/mp4"
                  }
                ],
                "body" : "from iOS",
                "createdAt" : "2024-01-02T03:04:05Z",
                "deleted" : false,
                "id" : "22222222-2222-2222-2222-222222222222",
                "sourcePlatform" : "ios",
                "title" : "iOS entry",
                "updatedAt" : "2024-01-02T03:04:05Z"
              }
            ]
        """.trimIndent()

        val parsed = VaultJsonTransfer.parseImport(iosJson.toByteArray(Charsets.UTF_8))
        assertEquals(1, parsed.entries.size)
        assertEquals(1, parsed.mediaStripped)
        val entry = parsed.entries.first()
        assertEquals("22222222-2222-2222-2222-222222222222", entry.id)
        assertEquals("from iOS", entry.body)
        assertEquals("ios", entry.sourcePlatform)
        assertTrue(entry.attachments.isEmpty())
        assertEquals(1_704_164_645_000L, entry.createdAt) // 2024-01-02T03:04:05Z
    }

    @Test
    fun export_includesAttachmentMetadata_whenPresent() {
        val entry = DiaryEntry(
            id = "33333333-3333-3333-3333-333333333333",
            body = "with media",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            attachments = listOf(
                DiaryMediaAttachment(
                    id = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                    kind = DiaryMediaKind.Image,
                    localFileName = "shot.jpg",
                    createdAt = 1_700_000_000_000L,
                ),
            ),
        )
        val json = VaultJsonTransfer.exportJSONString(listOf(entry))
        assertTrue(json.contains("\"kind\": \"image\""))
        assertTrue(json.contains("shot.jpg"))
    }
}
