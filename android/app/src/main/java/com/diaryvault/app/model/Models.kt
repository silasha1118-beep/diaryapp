package com.diaryvault.app.model

import java.util.UUID

data class DiaryEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val moodTag: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val preview: String
        get() {
            val trimmed = body.trim()
            return if (trimmed.length <= 120) trimmed else trimmed.take(120) + "…"
        }
}

enum class MirrorRole { User, Mirror }

data class MirrorMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MirrorRole,
    val text: String,
    val citedEntryIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

data class MirrorContext(
    val recentEntries: List<DiaryEntry>,
    val userDisplayName: String,
    val localeTag: String,
)

enum class AppLanguage(val tag: String, val shortLabelRes: Int) {
    ZhHant("zh-Hant", com.diaryvault.app.R.string.lang_zh_hant),
    ZhHans("zh-Hans", com.diaryvault.app.R.string.lang_zh_hans),
    English("en", com.diaryvault.app.R.string.lang_en);

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
                ?: when {
                    tag?.startsWith("zh-Hans") == true || tag?.startsWith("zh-CN") == true -> ZhHans
                    tag?.startsWith("zh") == true -> ZhHant
                    else -> English
                }
    }
}
