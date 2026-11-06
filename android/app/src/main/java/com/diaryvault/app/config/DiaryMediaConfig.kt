package com.diaryvault.app.config

import com.diaryvault.app.model.DiaryMediaAttachment
import com.diaryvault.app.model.DiaryMediaKind

/**
 * Soft caps for diary multimedia — mirrors iOS `DiaryMediaConfig`.
 * Upload **counts** stay in [UploadQuotaConfig] (same vault bucket as text).
 */
object DiaryMediaConfig {
    /** Suggested voice recording cap (~60–90s). Hard stop at the high end. */
    const val voiceMaxDurationSeconds = 90.0

    /** Suggested video recording / import cap (~30–60s). Hard stop at the high end. */
    const val videoMaxDurationSeconds = 60.0

    const val maxAttachmentsPerEntry = 24
    const val maxVoicePerEntry = 6
    const val maxImagesPerEntry = 12
    const val maxVideosPerEntry = 6

    /** JPEG compression quality for still photos (0–100). */
    const val imageJpegQuality = 72

    /** Longest edge for still photos before JPEG encode. */
    const val imageMaxPixelLength = 1920

    fun maxCount(forKind: DiaryMediaKind): Int = when (forKind) {
        DiaryMediaKind.Voice -> maxVoicePerEntry
        DiaryMediaKind.Image -> maxImagesPerEntry
        DiaryMediaKind.Video -> maxVideosPerEntry
    }

    fun canAdd(kind: DiaryMediaKind, attachments: List<DiaryMediaAttachment>): Boolean =
        remainingSlots(kind, attachments) > 0

    fun remainingSlots(kind: DiaryMediaKind, attachments: List<DiaryMediaAttachment>): Int {
        val kindRemaining = maxCount(kind) - attachments.count { it.kind == kind }
        val totalRemaining = maxAttachmentsPerEntry - attachments.size
        return maxOf(0, minOf(kindRemaining, totalRemaining))
    }
}
