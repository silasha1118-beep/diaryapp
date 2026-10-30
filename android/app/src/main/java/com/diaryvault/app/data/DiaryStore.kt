package com.diaryvault.app.data

import android.content.Context
import com.diaryvault.app.model.DiaryEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Local JSON persistence for diary entries. Cloud sync is out of scope for this scaffold. */
class DiaryStore(context: Context) {
    private val file = File(context.filesDir, "diary_entries.json")
    private val gson = Gson()
    private val _entries = MutableStateFlow<List<DiaryEntry>>(emptyList())
    val entries: StateFlow<List<DiaryEntry>> = _entries.asStateFlow()

    init {
        load()
        if (_entries.value.isEmpty()) {
            seedSampleIfNeeded()
        }
    }

    /** Vault list: newest createdAt first; never document-ID / insertion order. */
    val sortedEntries: List<DiaryEntry>
        get() = _entries.value.sortedWith(
            compareByDescending<DiaryEntry> { it.createdAt }
                .thenByDescending { it.updatedAt }
                .thenByDescending { it.id },
        )

    fun entry(id: String): DiaryEntry? = _entries.value.firstOrNull { it.id == id }

    fun add(title: String, body: String, moodTag: String?) {
        val now = System.currentTimeMillis()
        val entry = DiaryEntry(
            title = title.trim(),
            body = body.trim(),
            moodTag = moodTag?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = now,
            updatedAt = now,
        )
        _entries.value = listOf(entry) + _entries.value
        save()
    }

    fun update(entry: DiaryEntry) {
        val updated = entry.copy(updatedAt = System.currentTimeMillis())
        _entries.value = _entries.value.map { if (it.id == entry.id) updated else it }
        save()
    }

    fun delete(id: String) {
        _entries.value = _entries.value.filterNot { it.id == id }
        save()
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val type = object : TypeToken<List<DiaryEntry>>() {}.type
            val list: List<DiaryEntry> = gson.fromJson(file.readText(), type) ?: emptyList()
            _entries.value = list
        }
    }

    private fun save() {
        runCatching {
            file.writeText(gson.toJson(_entries.value))
        }
    }

    private fun seedSampleIfNeeded() {
        val sample = DiaryEntry(
            title = "",
            body = "今天把舊筆記本翻出來，發現幾年前寫過的一句話還在。想把這裡當成自己的金庫，慢慢寫，慢慢被理解。",
            moodTag = "平靜",
            createdAt = System.currentTimeMillis() - 86_400_000L,
            updatedAt = System.currentTimeMillis() - 86_400_000L,
        )
        _entries.value = listOf(sample)
        save()
    }
}
