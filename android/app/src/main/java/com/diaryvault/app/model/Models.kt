package com.diaryvault.app.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

/** Kind of media attached to a diary entry — matches iOS `DiaryMediaKind`. */
enum class DiaryMediaKind(val raw: String) {
    @SerializedName("voice")
    Voice("voice"),

    @SerializedName("image")
    Image("image"),

    @SerializedName("video")
    Video("video");

    val defaultFileExtension: String
        get() = when (this) {
            Voice -> "m4a"
            Image -> "jpg"
            Video -> "mp4"
        }

    val defaultMimeType: String
        get() = when (this) {
            Voice -> "audio/mp4"
            Image -> "image/jpeg"
            Video -> "video/mp4"
        }

    companion object {
        fun fromRaw(raw: String?): DiaryMediaKind? {
            if (raw.isNullOrBlank()) return null
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) }?.let { return it }
            // Legacy local Gson may have stored enum names before @SerializedName.
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        }
    }
}

/**
 * Local + cloud reference for one media file on a diary entry.
 * Local: filesDir/diary_media/{entryId}/{localFileName}
 * Cloud Storage: users/{uid}/entries/{entryId}/{localFileName}
 * Metadata always lives on the Firestore entry doc `attachments` array (never orphaned).
 */
data class DiaryMediaAttachment(
    val id: String = UUID.randomUUID().toString(),
    val kind: DiaryMediaKind,
    /** Filename only (no path separators) under the entry media folder. */
    val localFileName: String,
    /** Firebase Storage object path (null until uploaded). */
    val remotePath: String? = null,
    val mimeType: String = kind.defaultMimeType,
    val durationSeconds: Double? = null,
    val byteSize: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class DiaryEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val moodTag: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Soft-delete — keep forever in local JSON + Firestore; Vault hides when true. */
    val deleted: Boolean = false,
    val deletedAt: Long? = null,
    /**
     * Voice / image / video attachments. Synced on the same Firestore entry doc
     * (`attachments` + `hasMedia` + `contentType`). Local bytes under
     * `filesDir/diary_media/{entryId}/`; Storage path `users/{uid}/entries/{entryId}/{file}`.
     */
    val attachments: List<DiaryMediaAttachment> = emptyList(),
    /**
     * Which client first wrote this entry (`"ios"` / `"android"`).
     * Shared Firestore field `sourcePlatform` — preserved across edits/sync.
     */
    val sourcePlatform: String? = null,
) {
    val hasMedia: Boolean get() = attachments.isNotEmpty()

    /** Distinct media kinds present, stable order voice → image → video (list badges). */
    val mediaKindsPresent: List<DiaryMediaKind>
        get() = listOf(DiaryMediaKind.Voice, DiaryMediaKind.Image, DiaryMediaKind.Video)
            .filter { kind -> attachments.any { it.kind == kind } }

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
    val conversation: List<MirrorMessage> = emptyList(),
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
