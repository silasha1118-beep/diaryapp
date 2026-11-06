package com.diaryvault.app.sync

import com.diaryvault.app.config.FirebaseBootstrap
import com.diaryvault.app.model.DiaryEntry
import com.diaryvault.app.model.DiaryMediaAttachment
import com.diaryvault.app.model.DiaryMediaKind
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.tasks.await

/**
 * Firestore paths and field mapping for diary sync — matches iOS `DiaryCloudSync` / `DiaryFirestoreSchema`.
 *
 * ```
 * users/{uid}/entries/{entryId}
 *   id, title, body, moodTag?, createdAt, updatedAt,
 *   deleted, deletedAt?, status ("active" | "deleted"),
 *   hasMedia, ownerUid, schemaVersion, contentType ("text" | "multimedia"),
 *   sourcePlatform? ("ios" | "android"),
 *   attachments?: [{ id, kind, localFileName, remotePath?, mimeType, durationSeconds?, byteSize?, createdAt }],
 *   label?, bodyPreview?
 * ```
 * Soft-delete only — never hard-delete entry docs.
 * Media bytes: Storage `users/{uid}/entries/{entryId}/{fileName}`; metadata on this same entry doc.
 */
object DiaryFirestoreSchema {
    const val SCHEMA_VERSION = 1
    const val CONTENT_TYPE_TEXT = "text"
    const val CONTENT_TYPE_MULTIMEDIA = "multimedia"
    const val STATUS_ACTIVE = "active"
    const val STATUS_DELETED = "deleted"
    const val DATABASE_ID = "(default)"
    /** Shared with iOS — which client first wrote the entry. */
    const val SOURCE_PLATFORM_ANDROID = "android"
    const val SOURCE_PLATFORM_IOS = "ios"

    fun userDocument(uid: String) =
        FirebaseFirestore.getInstance().collection("users").document(uid)

    fun entriesCollection(uid: String) =
        userDocument(uid).collection("entries")

    fun entryDocument(uid: String, entryId: String) =
        entriesCollection(uid).document(entryId)
}

sealed class CloudSyncFailure(message: String) : Exception(message) {
    data object PermissionDenied : CloudSyncFailure(
        "Firestore permission denied. In Firebase Console → project matching Settings → Sync → paste firebase/firestore.rules and Publish.",
    )
    data class ServerMissingEntries(val localCount: Int, val serverCount: Int) : CloudSyncFailure(
        "Upload reported OK but server still has $serverCount entries while this device has $localCount. Confirm Console project ID, database (default), then Sync now.",
    )
    data object NotSignedIn : CloudSyncFailure(
        "No live Firebase Auth user — sign in with phone SMS first.",
    )
}

object DiaryCloudSync {
    /** This client’s `sourcePlatform` stamp for newly created entries. */
    const val SOURCE_PLATFORM = DiaryFirestoreSchema.SOURCE_PLATFORM_ANDROID

    /** True only for real Firebase Auth sessions (not stub / local synthetic ids). */
    val activeFirebaseUID: String?
        get() {
            if (!FirebaseBootstrap.isConfigured) return null
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            return uid?.takeIf { it.isNotBlank() }
        }

    val firebaseProjectID: String?
        get() = FirebaseBootstrap.projectId

    /**
     * Uploads pending Storage media (when [appContext] is provided), then writes Firestore.
     * Returns the entry with `remotePath` filled so callers can persist without bumping `updatedAt`.
     */
    suspend fun upsert(
        entry: DiaryEntry,
        uid: String,
        appContext: android.content.Context? = null,
    ): DiaryEntry {
        try {
            val withMedia = if (appContext != null && entry.attachments.isNotEmpty()) {
                val uploaded = DiaryMediaCloudSync.uploadPending(appContext, entry, uid)
                entry.copy(attachments = uploaded)
            } else {
                entry
            }
            val ref = DiaryFirestoreSchema.entryDocument(uid, withMedia.id)
            ref.set(encode(withMedia, uid), SetOptions.merge()).await()
            upsertUserProfile(uid, hasTextEntries = true)
            return withMedia
        } catch (e: Exception) {
            throw mapFirestoreError(e)
        }
    }

    suspend fun markDeleted(entryId: String, uid: String, at: Date) {
        try {
            val ref = DiaryFirestoreSchema.entryDocument(uid, entryId)
            ref.set(
                mapOf(
                    "deleted" to true,
                    "deletedAt" to Timestamp(at),
                    "status" to DiaryFirestoreSchema.STATUS_DELETED,
                    "updatedAt" to Timestamp(at),
                ),
                SetOptions.merge(),
            ).await()
            upsertUserProfile(uid, hasTextEntries = true)
        } catch (e: Exception) {
            throw mapFirestoreError(e)
        }
    }

    suspend fun upsertUserProfile(
        uid: String,
        displayName: String? = null,
        hasTextEntries: Boolean? = null,
    ) {
        try {
            val data = encodeUserProfile(
                uid = uid,
                authUser = FirebaseAuth.getInstance().currentUser,
                displayNameOverride = displayName,
                hasTextEntries = hasTextEntries,
            ).toMutableMap()
            data["updatedAt"] = Timestamp(Date())
            DiaryFirestoreSchema.userDocument(uid).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            throw mapFirestoreError(e)
        }
    }

    suspend fun fetchAllEntries(uid: String, source: Source = Source.SERVER): List<DiaryEntry> {
        try {
            val snapshot = DiaryFirestoreSchema.entriesCollection(uid).get(source).await()
            return snapshot.documents
                .mapNotNull { decode(it) }
                .sortedWith(chronologicalDescending)
        } catch (e: Exception) {
            throw mapFirestoreError(e)
        }
    }

    suspend fun fetchServerEntryIDs(uid: String): Set<String> =
        fetchAllEntries(uid, Source.SERVER).map { it.id }.toSet()

    suspend fun fetchActiveEntryIDs(uid: String): Set<String> =
        fetchAllEntries(uid, Source.SERVER).filter { !it.deleted }.map { it.id }.toSet()

    suspend fun waitForPendingWrites() {
        try {
            FirebaseFirestore.getInstance().waitForPendingWrites().await()
        } catch (e: Exception) {
            throw mapFirestoreError(e)
        }
    }

    suspend fun softDeleteAllEntries(uid: String) {
        try {
            val snapshot = DiaryFirestoreSchema.entriesCollection(uid).get().await()
            val now = Date()
            val db = FirebaseFirestore.getInstance()
            snapshot.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                for (doc in chunk) {
                    batch.set(
                        doc.reference,
                        mapOf(
                            "deleted" to true,
                            "deletedAt" to Timestamp(now),
                            "status" to DiaryFirestoreSchema.STATUS_DELETED,
                            "updatedAt" to Timestamp(now),
                        ),
                        SetOptions.merge(),
                    )
                }
                batch.commit().await()
            }
            upsertUserProfile(uid, hasTextEntries = false)
        } catch (e: Exception) {
            throw mapFirestoreError(e)
        }
    }

    val chronologicalDescending = compareByDescending<DiaryEntry> { it.createdAt }
        .thenByDescending { it.updatedAt }
        .thenByDescending { it.id }

    fun encode(entry: DiaryEntry, ownerUid: String): Map<String, Any> {
        val hasMedia = entry.hasMedia
        val contentType = if (hasMedia) {
            DiaryFirestoreSchema.CONTENT_TYPE_MULTIMEDIA
        } else {
            DiaryFirestoreSchema.CONTENT_TYPE_TEXT
        }
        val status = if (entry.deleted) {
            DiaryFirestoreSchema.STATUS_DELETED
        } else {
            DiaryFirestoreSchema.STATUS_ACTIVE
        }
        val data = mutableMapOf<String, Any>(
            "id" to entry.id,
            "title" to entry.title,
            "body" to entry.body,
            "createdAt" to Timestamp(Date(entry.createdAt)),
            "updatedAt" to Timestamp(Date(entry.updatedAt)),
            "ownerUid" to ownerUid,
            "schemaVersion" to DiaryFirestoreSchema.SCHEMA_VERSION,
            "contentType" to contentType,
            "label" to consoleLabel(entry),
            "bodyPreview" to bodyPreview(entry),
            "status" to status,
            "hasMedia" to hasMedia,
            "deleted" to entry.deleted,
            "attachments" to entry.attachments.map { encodeAttachment(it) },
            // Origin platform — preserve if known; stamp Android for legacy locals missing the field.
            "sourcePlatform" to (
                entry.sourcePlatform?.takeIf { it.isNotBlank() }
                    ?: DiaryFirestoreSchema.SOURCE_PLATFORM_ANDROID
                ),
        )
        entry.moodTag?.let { data["moodTag"] = it }
        if (entry.deleted) {
            val deletedAt = entry.deletedAt ?: entry.updatedAt
            data["deletedAt"] = Timestamp(Date(deletedAt))
        } else {
            // Clear stale deletedAt on restore (merge would otherwise leave the old field).
            data["deletedAt"] = FieldValue.delete()
        }
        return data
    }

    fun encodeAttachment(attachment: DiaryMediaAttachment): Map<String, Any> {
        val data = mutableMapOf<String, Any>(
            "id" to attachment.id,
            "kind" to attachment.kind.raw,
            "localFileName" to attachment.localFileName,
            "mimeType" to attachment.mimeType,
            "createdAt" to Timestamp(Date(attachment.createdAt)),
        )
        attachment.remotePath?.takeIf { it.isNotBlank() }?.let { data["remotePath"] = it }
        attachment.durationSeconds?.let { data["durationSeconds"] = it }
        attachment.byteSize?.let { data["byteSize"] = it }
        return data
    }

    fun encodeUserProfile(
        uid: String,
        authUser: com.google.firebase.auth.FirebaseUser?,
        displayNameOverride: String? = null,
        hasTextEntries: Boolean? = null,
    ): Map<String, Any> {
        val data = mutableMapOf<String, Any>(
            "ownerUid" to uid,
            "schemaVersion" to DiaryFirestoreSchema.SCHEMA_VERSION,
        )
        hasTextEntries?.let { data["hasTextEntries"] = it }
        if (authUser == null || authUser.uid != uid) {
            displayNameOverride?.trim()?.takeIf { it.isNotEmpty() }?.let { data["displayName"] = it }
            return data
        }
        authUser.phoneNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { data["phoneNumber"] = it }
        val overrideName = displayNameOverride?.trim()
        val authName = authUser.displayName?.trim()
        when {
            !overrideName.isNullOrEmpty() -> data["displayName"] = overrideName
            !authName.isNullOrEmpty() -> data["displayName"] = authName
        }
        val providers = mutableListOf<String>()
        for (info in authUser.providerData) {
            when (info.providerId) {
                "phone" -> if ("phone" !in providers) providers.add("phone")
                "apple.com" -> if ("apple" !in providers) providers.add("apple")
            }
        }
        if (providers.isNotEmpty()) data["authProviders"] = providers
        return data
    }

    fun decode(document: DocumentSnapshot): DiaryEntry? {
        val data = document.data ?: return null
        return decode(data, document.id)
    }

    fun decode(data: Map<String, Any?>, fallbackId: String): DiaryEntry? {
        val idString = (data["id"] as? String) ?: fallbackId
        if (runCatching { UUID.fromString(idString) }.isFailure) return null
        val title = data["title"] as? String ?: ""
        val body = data["body"] as? String ?: ""
        val moodTag = data["moodTag"] as? String
        val createdAt = dateMillis(data["createdAt"]) ?: 0L
        val updatedAt = dateMillis(data["updatedAt"]) ?: createdAt
        val deleted = when (val raw = data["deleted"]) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            else -> {
                // Prefer explicit bool; fall back to status string from Console-era docs.
                (data["status"] as? String)?.equals(
                    DiaryFirestoreSchema.STATUS_DELETED,
                    ignoreCase = true,
                ) == true
            }
        }
        val deletedAt = dateMillis(data["deletedAt"])
        val sourcePlatform = (data["sourcePlatform"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val attachments = decodeAttachments(data["attachments"])
        return DiaryEntry(
            id = idString,
            title = title,
            body = body,
            moodTag = moodTag,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deleted = deleted,
            deletedAt = deletedAt,
            attachments = attachments,
            sourcePlatform = sourcePlatform,
        )
    }

    fun decodeAttachments(raw: Any?): List<DiaryMediaAttachment> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            val map = item as? Map<String, Any?> ?: return@mapNotNull null
            decodeAttachment(map)
        }
    }

    fun decodeAttachment(data: Map<String, Any?>): DiaryMediaAttachment? {
        val idString = data["id"] as? String ?: return null
        if (runCatching { UUID.fromString(idString) }.isFailure) return null
        val kind = DiaryMediaKind.fromRaw(data["kind"] as? String) ?: return null
        val localRaw = (data["localFileName"] as? String)?.trim()
        val resolvedName = when {
            !localRaw.isNullOrEmpty() && !localRaw.contains("/") -> localRaw
            else -> {
                val remote = data["remotePath"] as? String
                remote?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
                    ?: "$idString.${kind.defaultFileExtension}"
            }
        }
        val mime = (data["mimeType"] as? String) ?: kind.defaultMimeType
        val remotePath = (data["remotePath"] as? String)?.takeIf { it.isNotBlank() }
        val duration = when (val n = data["durationSeconds"]) {
            is Number -> n.toDouble()
            else -> null
        }
        val byteSize = when (val n = data["byteSize"]) {
            is Number -> n.toInt()
            else -> null
        }
        val createdAt = dateMillis(data["createdAt"]) ?: System.currentTimeMillis()
        return DiaryMediaAttachment(
            id = idString,
            kind = kind,
            localFileName = resolvedName,
            remotePath = remotePath,
            mimeType = mime,
            durationSeconds = duration,
            byteSize = byteSize,
            createdAt = createdAt,
        )
    }

    fun consoleLabel(entry: DiaryEntry): String {
        val stamp = labelFormatter.format(Date(entry.createdAt))
        val title = entry.title.trim()
        if (title.isNotEmpty()) return "$stamp · $title"
        val snippet = bodyPreview(entry, 40)
        if (snippet.isNotEmpty()) return "$stamp · $snippet"
        return stamp
    }

    fun bodyPreview(entry: DiaryEntry, maxChars: Int = 120): String {
        val trimmed = entry.body.trim().replace("\n", " ")
        if (trimmed.isEmpty()) return ""
        return if (trimmed.length <= maxChars) trimmed else trimmed.take(maxChars) + "…"
    }

    fun dateMillis(raw: Any?): Long? {
        if (raw == null) return null
        when (raw) {
            is Timestamp -> return raw.toDate().time
            is Date -> return raw.time
            is Number -> {
                val value = raw.toDouble()
                return when {
                    value > 1_000_000_000_000 -> value.toLong()
                    value > 1_000_000_000 -> (value * 1000).toLong()
                    else -> null
                }
            }
            is String -> {
                return runCatching {
                    isoFormatter.parse(raw)?.time
                }.getOrNull()
            }
        }
        return null
    }

    fun mapFirestoreError(error: Exception): Exception {
        val message = error.message.orEmpty().lowercase()
        if (message.contains("permission") || message.contains("permission_denied")) {
            return CloudSyncFailure.PermissionDenied
        }
        if (error is CloudSyncFailure) return error
        return error
    }

    private val labelFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    private val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
