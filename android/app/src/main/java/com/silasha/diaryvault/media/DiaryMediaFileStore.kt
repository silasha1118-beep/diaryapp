package com.silasha.diaryvault.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.silasha.diaryvault.config.DiaryMediaConfig
import com.silasha.diaryvault.model.DiaryMediaAttachment
import com.silasha.diaryvault.model.DiaryMediaKind
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * On-device media files for diary attachments.
 * Layout: filesDir/diary_media/{entryId}/{attachmentId}.{ext}
 */
object DiaryMediaFileStore {
    fun rootDirectory(context: Context): File =
        File(context.filesDir, "diary_media")

    fun entryDirectory(context: Context, entryId: String): File =
        File(rootDirectory(context), entryId)

    fun file(context: Context, entryId: String, fileName: String): File =
        File(entryDirectory(context, entryId), fileName)

    fun absoluteFile(context: Context, entryId: String, attachment: DiaryMediaAttachment): File =
        file(context, entryId, attachment.localFileName)

    fun ensureEntryDirectory(context: Context, entryId: String): File {
        val dir = entryDirectory(context, entryId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun fileExists(context: Context, entryId: String, attachment: DiaryMediaAttachment): Boolean =
        absoluteFile(context, entryId, attachment).isFile

    /** Removes every file for an entry (cancel composer / wipe). */
    fun deleteAll(context: Context, entryId: String) {
        entryDirectory(context, entryId).deleteRecursively()
    }

    fun delete(context: Context, entryId: String, attachment: DiaryMediaAttachment) {
        absoluteFile(context, entryId, attachment).delete()
    }

    fun saveImageJpeg(
        context: Context,
        entryId: String,
        source: Uri,
        attachmentId: String = UUID.randomUUID().toString(),
    ): DiaryMediaAttachment {
        ensureEntryDirectory(context, entryId)
        val bitmap = context.contentResolver.openInputStream(source)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw MediaFileError.EncodeFailed
        val resized = resizeBitmap(bitmap, DiaryMediaConfig.imageMaxPixelLength)
        if (resized !== bitmap) bitmap.recycle()
        val fileName = "$attachmentId.${DiaryMediaKind.Image.defaultFileExtension}"
        val dest = file(context, entryId, fileName)
        FileOutputStream(dest).use { out ->
            if (!resized.compress(Bitmap.CompressFormat.JPEG, DiaryMediaConfig.imageJpegQuality, out)) {
                resized.recycle()
                throw MediaFileError.EncodeFailed
            }
        }
        resized.recycle()
        return DiaryMediaAttachment(
            id = attachmentId,
            kind = DiaryMediaKind.Image,
            localFileName = fileName,
            mimeType = DiaryMediaKind.Image.defaultMimeType,
            byteSize = dest.length().toInt(),
        )
    }

    fun saveImageJpegFromBitmap(
        context: Context,
        entryId: String,
        bitmap: Bitmap,
        attachmentId: String = UUID.randomUUID().toString(),
    ): DiaryMediaAttachment {
        ensureEntryDirectory(context, entryId)
        val resized = resizeBitmap(bitmap, DiaryMediaConfig.imageMaxPixelLength)
        val fileName = "$attachmentId.${DiaryMediaKind.Image.defaultFileExtension}"
        val dest = file(context, entryId, fileName)
        FileOutputStream(dest).use { out ->
            if (!resized.compress(Bitmap.CompressFormat.JPEG, DiaryMediaConfig.imageJpegQuality, out)) {
                if (resized !== bitmap) resized.recycle()
                throw MediaFileError.EncodeFailed
            }
        }
        if (resized !== bitmap) resized.recycle()
        return DiaryMediaAttachment(
            id = attachmentId,
            kind = DiaryMediaKind.Image,
            localFileName = fileName,
            mimeType = DiaryMediaKind.Image.defaultMimeType,
            byteSize = dest.length().toInt(),
        )
    }

    fun importVoiceRecording(
        context: Context,
        entryId: String,
        source: File,
        durationSeconds: Double?,
        attachmentId: String = UUID.randomUUID().toString(),
    ): DiaryMediaAttachment {
        ensureEntryDirectory(context, entryId)
        val fileName = "$attachmentId.${DiaryMediaKind.Voice.defaultFileExtension}"
        val dest = file(context, entryId, fileName)
        if (dest.exists()) dest.delete()
        source.copyTo(dest, overwrite = true)
        return DiaryMediaAttachment(
            id = attachmentId,
            kind = DiaryMediaKind.Voice,
            localFileName = fileName,
            mimeType = DiaryMediaKind.Voice.defaultMimeType,
            durationSeconds = durationSeconds,
            byteSize = dest.length().toInt(),
        )
    }

    /**
     * Import a video from content Uri. Rejects when duration exceeds the soft cap.
     * Copies as MP4 when possible; otherwise copies the source bytes.
     */
    fun importVideo(
        context: Context,
        entryId: String,
        source: Uri,
        attachmentId: String = UUID.randomUUID().toString(),
    ): DiaryMediaAttachment {
        ensureEntryDirectory(context, entryId)
        val duration = videoDurationSeconds(context, source)
        if (duration != null && duration > DiaryMediaConfig.videoMaxDurationSeconds + 0.5) {
            throw MediaFileError.VideoTooLong
        }
        val fileName = "$attachmentId.${DiaryMediaKind.Video.defaultFileExtension}"
        val dest = file(context, entryId, fileName)
        if (dest.exists()) dest.delete()
        context.contentResolver.openInputStream(source)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        } ?: throw MediaFileError.EncodeFailed
        return DiaryMediaAttachment(
            id = attachmentId,
            kind = DiaryMediaKind.Video,
            localFileName = fileName,
            mimeType = DiaryMediaKind.Video.defaultMimeType,
            durationSeconds = duration?.let { minOf(it, DiaryMediaConfig.videoMaxDurationSeconds) },
            byteSize = dest.length().toInt(),
        )
    }

    fun importVideoFile(
        context: Context,
        entryId: String,
        source: File,
        attachmentId: String = UUID.randomUUID().toString(),
    ): DiaryMediaAttachment {
        ensureEntryDirectory(context, entryId)
        val duration = videoDurationSeconds(source)
        if (duration != null && duration > DiaryMediaConfig.videoMaxDurationSeconds + 0.5) {
            throw MediaFileError.VideoTooLong
        }
        val fileName = "$attachmentId.${DiaryMediaKind.Video.defaultFileExtension}"
        val dest = file(context, entryId, fileName)
        if (dest.exists()) dest.delete()
        source.copyTo(dest, overwrite = true)
        return DiaryMediaAttachment(
            id = attachmentId,
            kind = DiaryMediaKind.Video,
            localFileName = fileName,
            mimeType = DiaryMediaKind.Video.defaultMimeType,
            durationSeconds = duration?.let { minOf(it, DiaryMediaConfig.videoMaxDurationSeconds) },
            byteSize = dest.length().toInt(),
        )
    }

    fun writeDownloadedData(
        context: Context,
        entryId: String,
        attachment: DiaryMediaAttachment,
        data: ByteArray,
    ) {
        ensureEntryDirectory(context, entryId)
        absoluteFile(context, entryId, attachment).writeBytes(data)
    }

    fun videoDurationSeconds(context: Context, uri: Uri): Double? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.div(1000.0)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun videoDurationSeconds(file: File): Double? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.div(1000.0)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxPixel: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxPixel || longest <= 0) return bitmap
        val scale = maxPixel.toFloat() / longest
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}

sealed class MediaFileError(message: String) : Exception(message) {
    data object EncodeFailed : MediaFileError("Could not encode the image.")
    data object VideoTooLong : MediaFileError("Video is longer than the suggested limit.")
    data object MissingFile : MediaFileError("Media file is missing on this device.")
}
