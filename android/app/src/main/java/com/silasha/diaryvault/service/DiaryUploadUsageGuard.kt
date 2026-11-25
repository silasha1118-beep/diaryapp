package com.silasha.diaryvault.service

import android.content.Context
import com.silasha.diaryvault.account.AccountSession
import com.silasha.diaryvault.config.UploadQuotaConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Local counter for **creating** diary vault uploads (not a server-side quota).
 * Text + multimedia share one vault bucket for **all** tiers (Free lifetime includes media).
 *
 * - Free: lifetime per account key (SharedPreferences; Android has no Firestore yet).
 * - Plus / Pro: daily (yyyy-MM-dd in default timezone).
 *
 * Soft ladders removed (2026-11-01) — hard stop → paywall.
 */
object DiaryUploadUsageGuard {
    private const val PREFS = "diary_vault_upload_usage"
    private const val KEY_VAULT_DAY = "vault.usageDay"
    private const val KEY_VAULT_COUNT = "vault.usageCount"
    private const val KEY_LIFETIME_PREFIX = "vault.lifetimeCount."
    private const val KEY_TEXT_DAY = "text.usageDay"
    private const val KEY_TEXT_COUNT = "text.usageCount"
    private const val KEY_MEDIA_DAY = "media.usageDay"
    private const val KEY_MEDIA_COUNT = "media.usageCount"
    private const val ANONYMOUS = "anonymous"

    class RateLimited(
        val kind: UploadQuotaConfig.Kind,
        val limit: Int,
        val isAuthorPlus: Boolean,
        val isAuthorPro: Boolean,
    ) : Exception("Vault upload limit reached ($limit) for $kind")

    fun effectiveLimit(
        kind: UploadQuotaConfig.Kind,
        isAuthorPlus: Boolean,
        isAuthorPro: Boolean = false,
    ): Int = UploadQuotaConfig.hardLimit(kind, isAuthorPlus, isAuthorPro)

    fun usageCount(
        context: Context,
        kind: UploadQuotaConfig.Kind,
        isAuthorPlus: Boolean,
        isAuthorPro: Boolean = false,
    ): Int {
        @Suppress("UNUSED_PARAMETER")
        kind
        return if (!isAuthorPlus && !isAuthorPro) {
            lifetimeCount(context, accountKey(context))
        } else {
            usageCountToday(context)
        }
    }

    /** Legacy name — Free returns lifetime usage. */
    fun usageCountToday(context: Context, kind: UploadQuotaConfig.Kind): Int {
        return usageCount(context, kind, isAuthorPlus = false, isAuthorPro = false)
    }

    fun isExhausted(
        context: Context,
        kind: UploadQuotaConfig.Kind,
        isAuthorPlus: Boolean,
        isAuthorPro: Boolean = false,
    ): Boolean {
        val limit = effectiveLimit(kind, isAuthorPlus, isAuthorPro)
        return usageCount(context, kind, isAuthorPlus, isAuthorPro) >= limit
    }

    /** Soft ladders removed — always false. */
    fun shouldNudgePaywall(
        context: Context,
        kind: UploadQuotaConfig.Kind,
        isAuthorPlus: Boolean,
        isAuthorPro: Boolean = false,
    ): Boolean {
        @Suppress("UNUSED_PARAMETER")
        context
        @Suppress("UNUSED_PARAMETER")
        kind
        @Suppress("UNUSED_PARAMETER")
        isAuthorPlus
        @Suppress("UNUSED_PARAMETER")
        isAuthorPro
        return false
    }

    /** Call before persisting a **new** diary create. Editing must not call this. */
    fun checkAndIncrement(
        context: Context,
        kind: UploadQuotaConfig.Kind,
        isAuthorPlus: Boolean = false,
        isAuthorPro: Boolean = false,
    ) {
        val limit = effectiveLimit(kind, isAuthorPlus, isAuthorPro)
        if (!isAuthorPlus && !isAuthorPro) {
            val key = accountKey(context)
            val count = lifetimeCount(context, key)
            if (count >= limit) {
                throw RateLimited(kind, limit, false, false)
            }
            setLifetimeCount(context, key, count + 1)
            return
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = dayStamp()
        val storedDay = prefs.getString(KEY_VAULT_DAY, null)
        var count = prefs.getInt(KEY_VAULT_COUNT, 0)
        if (storedDay != today) {
            count = migratedDailyCountIfNeeded(prefs, today, storedDay)
            prefs.edit().putString(KEY_VAULT_DAY, today).apply()
        }
        if (count >= limit) {
            throw RateLimited(kind, limit, isAuthorPlus, isAuthorPro)
        }
        prefs.edit().putInt(KEY_VAULT_COUNT, count + 1).apply()
    }

    /** Multimedia create — same vault bucket as text for Free/Plus/Pro. */
    fun checkAndIncrementMultimedia(
        context: Context,
        isAuthorPlus: Boolean = false,
        isAuthorPro: Boolean = false,
    ) {
        checkAndIncrement(context, UploadQuotaConfig.Kind.MULTIMEDIA, isAuthorPlus, isAuthorPro)
    }

    private fun usageCountToday(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = dayStamp()
        val storedDay = prefs.getString(KEY_VAULT_DAY, null)
        if (storedDay == today) {
            return prefs.getInt(KEY_VAULT_COUNT, 0).coerceAtLeast(0)
        }
        return migratedDailyCountIfNeeded(prefs, today, storedDay)
    }

    private fun migratedDailyCountIfNeeded(
        prefs: android.content.SharedPreferences,
        today: String,
        storedDay: String?,
    ): Int {
        if (storedDay == today) {
            return prefs.getInt(KEY_VAULT_COUNT, 0).coerceAtLeast(0)
        }
        var migrated = 0
        if (prefs.getString(KEY_TEXT_DAY, null) == today) {
            migrated += prefs.getInt(KEY_TEXT_COUNT, 0).coerceAtLeast(0)
        }
        if (prefs.getString(KEY_MEDIA_DAY, null) == today) {
            migrated += prefs.getInt(KEY_MEDIA_COUNT, 0).coerceAtLeast(0)
        }
        val editor = prefs.edit()
            .remove(KEY_TEXT_DAY)
            .remove(KEY_TEXT_COUNT)
            .remove(KEY_MEDIA_DAY)
            .remove(KEY_MEDIA_COUNT)
        if (migrated > 0) {
            editor.putString(KEY_VAULT_DAY, today).putInt(KEY_VAULT_COUNT, migrated)
        }
        editor.apply()
        return migrated
    }

    private fun accountKey(context: Context): String {
        val uid = AccountSession(context).userId?.takeIf { it.isNotBlank() }
        return uid ?: ANONYMOUS
    }

    private fun lifetimeCount(context: Context, accountKey: String): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_LIFETIME_PREFIX + accountKey, 0).coerceAtLeast(0)
    }

    private fun setLifetimeCount(context: Context, accountKey: String, count: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LIFETIME_PREFIX + accountKey, count.coerceAtLeast(0))
            .apply()
    }

    private fun dayStamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(Date())
    }
}
