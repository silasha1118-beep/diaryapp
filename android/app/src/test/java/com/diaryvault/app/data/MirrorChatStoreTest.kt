package com.diaryvault.app.data

import com.diaryvault.app.model.MirrorMessage
import com.diaryvault.app.model.MirrorRole
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gson shape used by [MirrorChatStore] — keep bubbles round-trippable. */
class MirrorChatStoreTest {
    private val gson = Gson()
    private val type = object : TypeToken<List<MirrorMessage>>() {}.type

    @Test
    fun mirrorMessages_roundTripThroughGson() {
        val original = listOf(
            MirrorMessage(id = "u1", role = MirrorRole.User, text = "你好", citedEntryIds = emptyList(), createdAt = 1L),
            MirrorMessage(
                id = "a1",
                role = MirrorRole.Mirror,
                text = "我在听。",
                citedEntryIds = listOf("entry-9"),
                createdAt = 2L,
            ),
        )
        val json = gson.toJson(original)
        val restored: List<MirrorMessage> = gson.fromJson(json, type)
        assertEquals(original, restored)
        assertEquals(MirrorRole.Mirror, restored[1].role)
        assertEquals(listOf("entry-9"), restored[1].citedEntryIds)
    }

    @Test
    fun accountKey_fallsBackWhenBlank() {
        assertEquals("anonymous", MirrorChatStore.accountKey(null))
        assertEquals("anonymous", MirrorChatStore.accountKey(""))
        assertEquals("uid-1", MirrorChatStore.accountKey("uid-1"))
        assertTrue(MirrorChatStore.MAX_MESSAGES >= 50)
    }
}
