package com.silasha.diaryvault.sync

import android.content.Context
import com.silasha.diaryvault.config.FirebaseBootstrap
import com.silasha.diaryvault.media.DiaryMediaFileStore
import com.silasha.diaryvault.media.MediaFileError
import com.silasha.diaryvault.model.DiaryEntry
import com.silasha.diaryvault.model.DiaryMediaAttachment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await

/**
 * Firebase Storage helpers for diary media under the signed-in user's uid path.
 *
 * Object path: `users/{uid}/entries/{entryId}/{localFileName}`
 * Metadata stays on the Firestore entry doc (`attachments` array). Soft-delete
 * hides the entry; Storage objects are retained with the archive.
 */
object DiaryMediaCloudSync {
    fun isAvailable(context: Context): Boolean {
        FirebaseBootstrap.configureIfNeeded(context.applicationContext)
        return FirebaseBootstrap.isConfigured && FirebaseAuth.getInstance().currentUser != null
    }

    fun storagePath(uid: String, entryId: String, fileName: String): String =
        "users/$uid/entries/$entryId/$fileName"

    /**
     * Uploads any local attachment that is missing `remotePath`.
     * Returns the attachments array with `remotePath` filled in.
     */
    suspend fun uploadPending(
        context: Context,
        entry: DiaryEntry,
        uid: String,
    ): List<DiaryMediaAttachment> {
        if (entry.attachments.isEmpty()) return entry.attachments
        FirebaseBootstrap.configureIfNeeded(context.applicationContext)
        if (!FirebaseBootstrap.isConfigured) return entry.attachments
        val storage = FirebaseStorage.getInstance()
        val updated = entry.attachments.toMutableList()
        for (index in updated.indices) {
            var item = updated[index]
            val local = DiaryMediaFileStore.absoluteFile(context, entry.id, item)
            if (!local.isFile) continue
            val path = storagePath(uid, entry.id, item.localFileName)
            if (item.remotePath == path) continue
            val ref = storage.reference.child(path)
            val metadata = StorageMetadata.Builder()
                .setContentType(item.mimeType)
                .build()
            ref.putFile(android.net.Uri.fromFile(local), metadata).await()
            item = item.copy(
                remotePath = path,
                byteSize = item.byteSize ?: local.length().toInt(),
            )
            updated[index] = item
        }
        return updated
    }

    /** Downloads a missing local file from Storage when `remotePath` is present. */
    suspend fun ensureLocalFile(
        context: Context,
        entryId: String,
        attachment: DiaryMediaAttachment,
    ): java.io.File {
        val local = DiaryMediaFileStore.absoluteFile(context, entryId, attachment)
        if (local.isFile) return local
        val remotePath = attachment.remotePath?.takeIf { it.isNotBlank() }
            ?: throw MediaFileError.MissingFile
        FirebaseBootstrap.configureIfNeeded(context.applicationContext)
        if (!FirebaseBootstrap.isConfigured) throw MediaFileError.MissingFile
        DiaryMediaFileStore.ensureEntryDirectory(context, entryId)
        val ref = FirebaseStorage.getInstance().reference.child(remotePath)
        ref.getFile(local).await()
        return local
    }
}
