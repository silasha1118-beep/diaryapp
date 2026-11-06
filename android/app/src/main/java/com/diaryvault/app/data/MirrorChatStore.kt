package com.diaryvault.app.data

import android.content.Context
import android.util.Log
import com.diaryvault.app.model.MirrorMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Local JSON persistence for Mirror chat bubbles (device-only; no cloud Mirror transcript yet).
 * Scoped per account key so switching phones/accounts does not leak prior threads.
 */
class MirrorChatStore(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val type = object : TypeToken<List<MirrorMessage>>() {}.type

    fun load(accountKey: String): List<MirrorMessage> {
        val file = fileFor(accountKey)
        if (!file.exists()) return emptyList()
        return try {
            gson.fromJson<List<MirrorMessage>>(file.readText(), type)?.filterNotNull().orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load Mirror chat for $accountKey", e)
            emptyList()
        }
    }

    fun save(accountKey: String, messages: List<MirrorMessage>) {
        val trimmed = if (messages.size > MAX_MESSAGES) {
            messages.takeLast(MAX_MESSAGES)
        } else {
            messages
        }
        val file = fileFor(accountKey)
        try {
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(trimmed))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save Mirror chat for $accountKey", e)
        }
    }

    fun clear(accountKey: String) {
        try {
            fileFor(accountKey).delete()
        } catch (_: Exception) {
            // best-effort
        }
    }

    private fun fileFor(accountKey: String): File {
        val safe = accountKey.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { ANON_KEY }
        return File(appContext.filesDir, "mirror_chat_$safe.json")
    }

    companion object {
        private const val TAG = "MirrorChatStore"
        private const val ANON_KEY = "anonymous"
        /** Soft cap so the JSON file cannot grow without bound. */
        const val MAX_MESSAGES = 200

        fun accountKey(userId: String?): String =
            userId?.takeIf { it.isNotBlank() } ?: ANON_KEY
    }
}
