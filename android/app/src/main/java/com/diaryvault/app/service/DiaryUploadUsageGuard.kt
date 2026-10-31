package com.diaryvault.app.service

import android.content.Context
import com.diaryvault.app.config.UploadQuotaConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Local daily counter for **creating** diary uploads (not a server-side quota).
 * Gates new text entries now; multimedia paths ready for DA-15.
 *
 * Day stamp matches [MirrorUsageGuard] (`yyyy-MM-dd` in default timezone).
 */
object DiaryUploadUsageGuard {
    private const val PREFS = "diary_vault_upload_usage"
    private const val KEY_TEXT_DAY = "text.usageDay"
    private const val KEY_TEXT_COUNT = "text.usageCount"
    private const val KEY_MEDIA_DAY = "media.usageDay"
    private const val KEY_MEDIA_COUNT = "media.usageCount"

    class RateLimited(
        val kind: UploadQuotaConfig.Kind,
        val limit: Int,
        val isAuthorPlus: Boolean,
        val isAuthorPro: Boolean,
    ) : Exception("Daily upload limit reached ($limit) for $kind")

    fun effectiveLimit(
        kind: UploadQuotaConfig.Kind,
        isAuthorPlus: Boolean,
        isAuthorPro: Boolean = false,
    ): Int = UploadQuotaConfig.hardLimit(kind, isAuthorPlus, isAuthorPro)

    fun usageCountToday(context: Context, kind: UploadQuotaConfig.Kind): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = dayStamp()
        val (dayKey, countKey) = keys(kind)
        if (prefs.getString(dayKey, null) != today) return 0
        return prefs.getInt(countKey, 0).coerceAtLeast(0)
    }

    fun isExhausted(
        context: Context,
        kind: UploadQuotaConfig.Kind,
        isAuthorPlus: Boolean,
        isAuthorPro: Boolean = false,
    ): Boolean {
        val limit = effectiveLimit(kind, isAuthorPlus, isAuthorPro)
        return usageCountToday(context, kind) >= limit
    }

    fun shouldNudgePaywall(
        context: Context,
        kind: UploadQuotaConfig.Kind,
        isAuthorPlus: Boolean,
        isAuthorPro: Boolean = false,
    ): Boolean {
        if (isAuthorPlus || isAuthorPro) return false
        if (isExhausted(context, kind, false, false)) return false
        val threshold = UploadQuotaConfig.softThreshold(kind)
        if (threshold <= 0) return false
        return usageCountToday(context, kind) >= threshold
    }

    /** Call before persisting a **new** diary create. Editing must not call this. */
    fun checkAndIncrement(
        context: Context,
        kind: UploadQuotaConfig.Kind,
        isAuthorPlus: Boolean = false,
        isAuthorPro: Boolean = false,
    ) {
        val limit = effectiveLimit(kind, isAuthorPlus, isAuthorPro)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = dayStamp()
        val (dayKey, countKey) = keys(kind)
        val storedDay = prefs.getString(dayKey, null)
        var count = prefs.getInt(countKey, 0)
        if (storedDay != today) {
            count = 0
            prefs.edit().putString(dayKey, today).apply()
        }
        if (count >= limit) {
            throw RateLimited(kind, limit, isAuthorPlus, isAuthorPro)
        }
        prefs.edit().putInt(countKey, count + 1).apply()
    }

    /** DA-15 entry point — same semantics as text; no UI caller yet. */
    fun checkAndIncrementMultimedia(
        context: Context,
        isAuthorPlus: Boolean = false,
        isAuthorPro: Boolean = false,
    ) {
        checkAndIncrement(context, UploadQuotaConfig.Kind.MULTIMEDIA, isAuthorPlus, isAuthorPro)
    }

    private fun keys(kind: UploadQuotaConfig.Kind): Pair<String, String> = when (kind) {
        UploadQuotaConfig.Kind.TEXT -> KEY_TEXT_DAY to KEY_TEXT_COUNT
        UploadQuotaConfig.Kind.MULTIMEDIA -> KEY_MEDIA_DAY to KEY_MEDIA_COUNT
    }

    private fun dayStamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(Date())
    }
}
