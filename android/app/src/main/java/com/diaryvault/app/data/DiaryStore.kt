package com.diaryvault.app.data

import android.content.Context
import android.util.Log
import com.diaryvault.app.model.DiaryEntry
import com.diaryvault.app.model.DiaryMediaAttachment
import com.diaryvault.app.sync.CloudSyncFailure
import com.diaryvault.app.sync.DiaryCloudSync
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Local JSON persistence for diary entries (source of truth offline).
 * When Firebase Auth is signed in, entries sync to Firestore + Storage (soft-delete, same shape as iOS).
 */
class DiaryStore(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val file = File(context.filesDir, "diary_entries.json")
    private val pendingFile = File(context.filesDir, "diary_cloud_pending.json")
    private val gson = Gson()
    private val _entries = MutableStateFlow<List<DiaryEntry>>(emptyList())
    val entries: StateFlow<List<DiaryEntry>> = _entries.asStateFlow()

    private val _lastCloudSyncAt = MutableStateFlow<Long?>(null)
    val lastCloudSyncAt: StateFlow<Long?> = _lastCloudSyncAt.asStateFlow()

    private val _lastCloudSyncError = MutableStateFlow<String?>(null)
    val lastCloudSyncError: StateFlow<String?> = _lastCloudSyncError.asStateFlow()

    private val _lastCloudSyncServerCount = MutableStateFlow<Int?>(null)
    val lastCloudSyncServerCount: StateFlow<Int?> = _lastCloudSyncServerCount.asStateFlow()

    private val _lastCloudSyncProjectID = MutableStateFlow<String?>(null)
    val lastCloudSyncProjectID: StateFlow<String?> = _lastCloudSyncProjectID.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var pendingOps: MutableList<PendingCloudOp> = mutableListOf()
    private var debounceJob: Job? = null
    private val syncMutex = Mutex()
    private var needsResync = false

    init {
        load()
        loadPending()
        if (_entries.value.isEmpty()) {
            seedSampleIfNeeded()
        }
    }

    /** Vault list: active only, newest createdAt first. */
    val sortedEntries: List<DiaryEntry>
        get() = _entries.value
            .filter { !it.deleted }
            .sortedWith(DiaryCloudSync.chronologicalDescending)

    fun entry(id: String): DiaryEntry? =
        _entries.value.firstOrNull { it.id == id && !it.deleted }

    private fun storedEntry(id: String): DiaryEntry? =
        _entries.value.firstOrNull { it.id == id }

    fun add(
        title: String,
        body: String,
        moodTag: String?,
        attachments: List<DiaryMediaAttachment> = emptyList(),
        entryId: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val entry = DiaryEntry(
            id = entryId ?: java.util.UUID.randomUUID().toString(),
            title = title.trim(),
            body = body.trim(),
            moodTag = moodTag?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = now,
            updatedAt = now,
            attachments = attachments,
            sourcePlatform = DiaryCloudSync.SOURCE_PLATFORM,
        )
        _entries.value = listOf(entry) + _entries.value
        save()
        enqueue(PendingCloudOp.Upsert(entry.id))
        scheduleSynchronize()
    }

    fun update(entry: DiaryEntry) {
        val existing = storedEntry(entry.id)
        val updated = entry.copy(
            deleted = false,
            deletedAt = null,
            updatedAt = System.currentTimeMillis(),
            // Composer may pass an explicit attachments list (including empty after removals).
            attachments = entry.attachments,
            // Keep origin platform; fill Android only for pre-field legacy rows.
            sourcePlatform = entry.sourcePlatform?.takeIf { it.isNotBlank() }
                ?: existing?.sourcePlatform?.takeIf { it.isNotBlank() }
                ?: DiaryCloudSync.SOURCE_PLATFORM,
        )
        _entries.value = _entries.value.map { if (it.id == entry.id) updated else it }
        save()
        enqueue(PendingCloudOp.Upsert(updated.id))
        scheduleSynchronize()
    }

    /**
     * Persist Storage `remotePath` updates from a successful upload without bumping `updatedAt`
     * (avoids sync loops). Mirrors iOS `DiaryStore.applyCloudMediaPaths`.
     */
    fun applyCloudMediaPaths(entry: DiaryEntry) {
        val index = _entries.value.indexOfFirst { it.id == entry.id }
        if (index < 0) return
        val local = _entries.value[index]
        if (local.attachments == entry.attachments) return
        val merged = local.copy(attachments = entry.attachments)
        _entries.value = _entries.value.map { if (it.id == entry.id) merged else it }
        save()
    }

    /** Soft-delete locally + enqueue Firestore soft-delete upsert. Never hard-removes the row. */
    fun delete(id: String) {
        val now = System.currentTimeMillis()
        var changed = false
        _entries.value = _entries.value.map { entry ->
            if (entry.id == id && !entry.deleted) {
                changed = true
                entry.copy(deleted = true, deletedAt = now, updatedAt = now)
            } else {
                entry
            }
        }
        if (!changed) return
        save()
        enqueue(PendingCloudOp.Upsert(id))
        scheduleSynchronize()
    }

    fun bindCloudSync() {
        scheduleSynchronize()
    }

    fun scheduleSynchronize() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(250)
            synchronizeWithCloud()
        }
    }

    suspend fun synchronizeWithCloud() {
        if (!syncMutex.tryLock()) {
            needsResync = true
            return
        }
        try {
            val uid = DiaryCloudSync.activeFirebaseUID ?: return
            _isSyncing.value = true
            _lastCloudSyncProjectID.value = DiaryCloudSync.firebaseProjectID

            val hasActive = _entries.value.any { !it.deleted }
            runCatching {
                DiaryCloudSync.upsertUserProfile(uid, hasTextEntries = hasActive)
            }

            var remote: List<DiaryEntry> = emptyList()
            try {
                remote = DiaryCloudSync.fetchAllEntries(uid)
                mergeRemote(remote)
            } catch (e: Exception) {
                Log.w(TAG, "DiaryCloudSync pull failed", e)
            }

            try {
                flushPending(uid)
                pushMissingLocals(uid, remote.map { it.id }.toSet())
                DiaryCloudSync.waitForPendingWrites()
                verifyLocalsOnServer(uid)
                val serverActive = DiaryCloudSync.fetchActiveEntryIDs(uid)
                _lastCloudSyncServerCount.value = serverActive.size
                _lastCloudSyncAt.value = System.currentTimeMillis()
                _lastCloudSyncError.value = null
                Log.d(
                    TAG,
                    "DiaryCloudSync OK project=${DiaryCloudSync.firebaseProjectID} uid=$uid " +
                        "localActive=${_entries.value.count { !it.deleted }} serverActive=${serverActive.size}",
                )
            } catch (e: Exception) {
                _lastCloudSyncError.value = e.message ?: e.toString()
                Log.w(TAG, "DiaryCloudSync push/verify failed", e)
            }
        } finally {
            _isSyncing.value = false
            syncMutex.unlock()
            if (needsResync) {
                needsResync = false
                scheduleSynchronize()
            }
        }
    }

    private suspend fun verifyLocalsOnServer(uid: String): Set<String> {
        val localIDs = _entries.value.map { it.id }.toSet()
        if (localIDs.isEmpty()) return DiaryCloudSync.fetchServerEntryIDs(uid)

        var serverIDs = DiaryCloudSync.fetchServerEntryIDs(uid)
        val missing = localIDs - serverIDs
        if (missing.isNotEmpty()) {
            for (entry in _entries.value.filter { it.id in missing }) {
                val written = DiaryCloudSync.upsert(entry, uid, appContext)
                applyCloudMediaPaths(written)
            }
            DiaryCloudSync.waitForPendingWrites()
            serverIDs = DiaryCloudSync.fetchServerEntryIDs(uid)
        }
        val stillMissing = localIDs - serverIDs
        if (stillMissing.isNotEmpty()) {
            throw CloudSyncFailure.ServerMissingEntries(localIDs.size, serverIDs.size)
        }
        return serverIDs
    }

    private fun mergeRemote(remote: List<DiaryEntry>) {
        var changed = false
        val merged = _entries.value.toMutableList()
        for (remoteEntry in remote) {
            val index = merged.indexOfFirst { it.id == remoteEntry.id }
            if (index >= 0) {
                if (remoteEntry.updatedAt > merged[index].updatedAt) {
                    merged[index] = remoteEntry
                    changed = true
                }
            } else {
                val blockedByPending = pendingOps.any { op ->
                    when (op) {
                        is PendingCloudOp.Delete -> op.id == remoteEntry.id
                        is PendingCloudOp.Upsert -> {
                            op.id == remoteEntry.id &&
                                storedEntry(op.id)?.let {
                                    it.deleted && it.updatedAt >= remoteEntry.updatedAt
                                } == true
                        }
                    }
                }
                if (!blockedByPending) {
                    merged.add(remoteEntry)
                    changed = true
                }
            }
        }
        if (changed) {
            _entries.value = merged.sortedWith(DiaryCloudSync.chronologicalDescending)
            save()
        }
    }

    private suspend fun flushPending(uid: String) {
        if (pendingOps.isEmpty()) return
        val latest = LinkedHashMap<String, PendingCloudOp>()
        for (op in pendingOps) latest[op.entryId] = op
        val remaining = mutableListOf<PendingCloudOp>()
        var firstError: Exception? = null
        for (op in latest.values) {
            try {
                when (op) {
                    is PendingCloudOp.Upsert -> {
                        storedEntry(op.id)?.let { local ->
                            val written = DiaryCloudSync.upsert(local, uid, appContext)
                            applyCloudMediaPaths(written)
                        }
                    }
                    is PendingCloudOp.Delete -> {
                        val entry = storedEntry(op.id)
                        if (entry != null) {
                            val written = DiaryCloudSync.upsert(entry, uid, appContext)
                            applyCloudMediaPaths(written)
                        } else {
                            DiaryCloudSync.markDeleted(op.id, uid, java.util.Date())
                        }
                    }
                }
            } catch (e: Exception) {
                remaining.add(op)
                if (firstError == null) firstError = e
            }
        }
        pendingOps = remaining
        persistPending()
        firstError?.let { throw it }
    }

    private suspend fun pushMissingLocals(uid: String, remoteIDs: Set<String>) {
        for (entry in _entries.value.filter { it.id !in remoteIDs }) {
            val written = DiaryCloudSync.upsert(entry, uid, appContext)
            applyCloudMediaPaths(written)
        }
    }

    private sealed class PendingCloudOp {
        abstract val entryId: String
        data class Upsert(val id: String) : PendingCloudOp() {
            override val entryId: String get() = id
        }
        data class Delete(val id: String) : PendingCloudOp() {
            override val entryId: String get() = id
        }
    }

    private fun enqueue(op: PendingCloudOp) {
        if (DiaryCloudSync.activeFirebaseUID == null) return
        pendingOps.removeAll { it.entryId == op.entryId }
        pendingOps.add(op)
        persistPending()
    }

    private fun loadPending() {
        if (!pendingFile.exists()) return
        runCatching {
            val type = object : TypeToken<List<PendingDto>>() {}.type
            val list: List<PendingDto> = gson.fromJson(pendingFile.readText(), type) ?: emptyList()
            pendingOps = list.mapNotNull { dto ->
                when (dto.kind) {
                    "upsert" -> PendingCloudOp.Upsert(dto.id)
                    "delete" -> PendingCloudOp.Delete(dto.id)
                    else -> null
                }
            }.toMutableList()
        }
    }

    private fun persistPending() {
        runCatching {
            val dtos = pendingOps.map {
                when (it) {
                    is PendingCloudOp.Upsert -> PendingDto("upsert", it.id)
                    is PendingCloudOp.Delete -> PendingDto("delete", it.id)
                }
            }
            pendingFile.writeText(gson.toJson(dtos))
        }
    }

    private data class PendingDto(val kind: String, val id: String)

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val type = object : TypeToken<List<DiaryEntry>>() {}.type
            val list: List<DiaryEntry> = gson.fromJson(file.readText(), type) ?: emptyList()
            _entries.value = list.sortedWith(DiaryCloudSync.chronologicalDescending)
        }
    }

    private fun save() {
        runCatching {
            val all = _entries.value.sortedWith(DiaryCloudSync.chronologicalDescending)
            file.writeText(gson.toJson(all))
        }
    }

    private fun seedSampleIfNeeded() {
        val sample = DiaryEntry(
            title = "",
            body = "今天把舊筆記本翻出來，發現幾年前寫過的一句話還在。想把這裡當成自己的金庫，慢慢寫，慢慢被理解。",
            moodTag = "平靜",
            createdAt = System.currentTimeMillis() - 86_400_000L,
            updatedAt = System.currentTimeMillis() - 86_400_000L,
            sourcePlatform = DiaryCloudSync.SOURCE_PLATFORM,
        )
        _entries.value = listOf(sample)
        save()
    }

    companion object {
        private const val TAG = "DiaryStore"
    }
}
