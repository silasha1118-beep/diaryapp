package com.diaryvault.app.data

import com.diaryvault.app.model.DiaryEntry
import com.diaryvault.app.model.DiaryMediaAttachment
import com.diaryvault.app.model.DiaryMediaKind
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.TreeMap

/**
 * Cross-platform vault JSON transfer matching iOS `DiaryStore.exportJSONData` /
 * `importVaultJSON`:
 * - pretty-printed JSON **array** of diary entries
 * - ISO-8601 dates
 * - sorted object keys
 * - active entries only on export
 * - merge-by-id import (caller applies); media metadata may appear in file
 *
 * Uses Gson (not Android `org.json`) so JVM unit tests exercise the real codec.
 */
object VaultJsonTransfer {

    data class VaultImportResult(
        val added: Int,
        val skippedDuplicates: Int,
        val mediaStripped: Int,
        val totalInFile: Int,
    )

    /** Parsed entry ready to merge (attachments already cleared when stripped). */
    data class ParsedImport(
        val entries: List<DiaryEntry>,
        val mediaStripped: Int,
    )

    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    fun exportJSONBytes(entries: List<DiaryEntry>): ByteArray =
        exportJSONString(entries).toByteArray(Charsets.UTF_8)

    fun exportJSONString(entries: List<DiaryEntry>): String {
        val arr = JsonArray()
        for (entry in entries) {
            arr.add(entryToJson(entry))
        }
        return prettyGson.toJson(arr) + "\n"
    }

    fun parseImport(data: ByteArray): ParsedImport {
        val text = data.toString(Charsets.UTF_8).trim()
        if (text.isEmpty()) throw IllegalArgumentException("empty vault JSON")
        val root = JsonParser.parseString(text)
        if (!root.isJsonArray) throw IllegalArgumentException("vault JSON must be an array")
        val arr = root.asJsonArray
        val out = ArrayList<DiaryEntry>(arr.size())
        var mediaStripped = 0
        for (element in arr) {
            if (!element.isJsonObject) continue
            val obj = element.asJsonObject
            val rawAttachments = obj.getAsJsonArray("attachments")
            val hadMedia = rawAttachments != null && rawAttachments.size() > 0
            // Export is text JSON only — do not carry old-uid Storage paths.
            val entry = entryFromJson(obj).copy(attachments = emptyList())
            if (hadMedia) mediaStripped += 1
            out.add(entry)
        }
        return ParsedImport(entries = out, mediaStripped = mediaStripped)
    }

    private fun entryToJson(entry: DiaryEntry): JsonObject {
        val map = TreeMap<String, Any?>()
        map["attachments"] = attachmentsToJson(entry.attachments)
        map["body"] = entry.body
        map["createdAt"] = formatIso(entry.createdAt)
        map["deleted"] = entry.deleted
        entry.deletedAt?.let { map["deletedAt"] = formatIso(it) }
        map["id"] = entry.id
        entry.moodTag?.let { map["moodTag"] = it }
        entry.sourcePlatform?.let { map["sourcePlatform"] = it }
        map["title"] = entry.title
        map["updatedAt"] = formatIso(entry.updatedAt)
        return mapToJsonObject(map)
    }

    private fun attachmentsToJson(attachments: List<DiaryMediaAttachment>): JsonArray {
        val arr = JsonArray()
        for (att in attachments) {
            val map = TreeMap<String, Any?>()
            map["createdAt"] = formatIso(att.createdAt)
            map["id"] = att.id
            map["kind"] = att.kind.raw
            map["localFileName"] = att.localFileName
            map["mimeType"] = att.mimeType
            att.byteSize?.let { map["byteSize"] = it }
            att.durationSeconds?.let { map["durationSeconds"] = it }
            att.remotePath?.let { map["remotePath"] = it }
            arr.add(mapToJsonObject(map))
        }
        return arr
    }

    private fun mapToJsonObject(map: TreeMap<String, Any?>): JsonObject {
        val obj = JsonObject()
        for ((key, value) in map) {
            when (value) {
                null -> obj.add(key, JsonNull.INSTANCE)
                is JsonArray -> obj.add(key, value)
                is JsonObject -> obj.add(key, value)
                is Boolean -> obj.add(key, JsonPrimitive(value))
                is Number -> obj.add(key, JsonPrimitive(value))
                is String -> obj.add(key, JsonPrimitive(value))
                else -> obj.add(key, JsonPrimitive(value.toString()))
            }
        }
        return obj
    }

    private fun entryFromJson(obj: JsonObject): DiaryEntry {
        val id = obj.getStringOrNull("id")?.trim().orEmpty()
        if (id.isEmpty()) throw IllegalArgumentException("entry missing id")
        val attachmentsArr = obj.getAsJsonArray("attachments")
        val attachments = buildList {
            if (attachmentsArr != null) {
                for (element in attachmentsArr) {
                    if (!element.isJsonObject) continue
                    add(attachmentFromJson(element.asJsonObject))
                }
            }
        }
        return DiaryEntry(
            id = id,
            title = obj.getStringOrNull("title").orEmpty(),
            body = obj.getStringOrNull("body").orEmpty(),
            moodTag = obj.getStringOrNull("moodTag"),
            createdAt = parseFlexibleDate(obj, "createdAt"),
            updatedAt = parseFlexibleDate(obj, "updatedAt"),
            deleted = obj.get("deleted")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            deletedAt = parseFlexibleDateOrNull(obj, "deletedAt"),
            attachments = attachments,
            sourcePlatform = obj.getStringOrNull("sourcePlatform"),
        )
    }

    private fun attachmentFromJson(obj: JsonObject): DiaryMediaAttachment {
        val kindRaw = obj.getStringOrNull("kind") ?: "image"
        val kind = DiaryMediaKind.fromRaw(kindRaw) ?: DiaryMediaKind.Image
        return DiaryMediaAttachment(
            id = obj.getStringOrNull("id")?.takeIf { it.isNotBlank() }
                ?: java.util.UUID.randomUUID().toString(),
            kind = kind,
            localFileName = obj.getStringOrNull("localFileName") ?: "media",
            remotePath = obj.getStringOrNull("remotePath"),
            mimeType = obj.getStringOrNull("mimeType")?.takeIf { it.isNotBlank() }
                ?: kind.defaultMimeType,
            durationSeconds = obj.get("durationSeconds")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asDouble,
            byteSize = obj.get("byteSize")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asInt,
            createdAt = parseFlexibleDateOrNull(obj, "createdAt") ?: System.currentTimeMillis(),
        )
    }

    private fun JsonObject.getStringOrNull(key: String): String? {
        val el = get(key) ?: return null
        if (el.isJsonNull || !el.isJsonPrimitive) return null
        val v = el.asString.trim()
        return v.takeIf { it.isNotEmpty() }
    }

    private fun parseFlexibleDate(obj: JsonObject, key: String): Long {
        return parseFlexibleDateOrNull(obj, key)
            ?: throw IllegalArgumentException("entry missing $key")
    }

    /**
     * Accepts iOS ISO-8601 strings and Android/Gson epoch millis (number or numeric string).
     */
    private fun parseFlexibleDateOrNull(obj: JsonObject, key: String): Long? {
        val el = obj.get(key) ?: return null
        if (el.isJsonNull) return null
        if (!el.isJsonPrimitive) return null
        val prim = el.asJsonPrimitive
        if (prim.isNumber) return prim.asLong
        if (prim.isString) {
            val trimmed = prim.asString.trim()
            if (trimmed.isEmpty()) return null
            trimmed.toLongOrNull()?.let { return it }
            return parseIso(trimmed)
        }
        return null
    }

    private fun formatIso(epochMs: Long): String {
        // Match Foundation JSONEncoder `.iso8601` (no fractional seconds).
        return isoFormatter().format(Date(epochMs))
    }

    private fun parseIso(value: String): Long {
        val variants = listOf(
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        )
        for (pattern in variants) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }
                return fmt.parse(value)?.time ?: continue
            } catch (_: ParseException) {
                // try next
            }
        }
        throw IllegalArgumentException("unrecognized date: $value")
    }

    private fun isoFormatter(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
}
