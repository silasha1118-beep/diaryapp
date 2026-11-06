package com.diaryvault.app.service

import android.content.Context
import com.diaryvault.app.account.AccountSession
import com.diaryvault.app.config.AIConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Local Mirror AI request counter (not a server-side quota).
 *
 * Free: lifetime hard cap (default 40). Plus: 8/day. Pro: 16/day.
 * Soft ladders removed (2026-11-01). Independent of diary upload quotas
 * ([com.diaryvault.app.config.UploadQuotaConfig] / [DiaryUploadUsageGuard]).
 *
 * Pass [isAuthorPlus] / [isAuthorPro] from billing entitlement
 * (Play Billing stub today — iOS StoreKit is the first wired path).
 */
object MirrorUsageGuard {
    private const val PREFS = "diary_vault_ai_usage"
    private const val KEY_DAY = "usageDay"
    private const val KEY_COUNT = "usageCount"
    private const val KEY_LIFETIME_PREFIX = "lifetimeCount."
    private const val ANONYMOUS = "anonymous"

    fun effectiveLimit(isAuthorPlus: Boolean, isAuthorPro: Boolean = false): Int {
        return when {
            isAuthorPro -> AIConfig.proDailyRequestLimit
            isAuthorPlus -> AIConfig.dailyRequestLimit
            else -> AIConfig.freeLifetimeRequestLimit
        }
    }

    fun usageCount(
        context: Context,
        isAuthorPlus: Boolean,
        isAuthorPro: Boolean = false,
    ): Int {
        return if (!isAuthorPlus && !isAuthorPro) {
            lifetimeCount(context, accountKey(context))
        } else {
            usageCountToday(context)
        }
    }

    fun usageCountToday(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = dayStamp()
        if (prefs.getString(KEY_DAY, null) != today) return 0
        return prefs.getInt(KEY_COUNT, 0).coerceAtLeast(0)
    }

    /** Remaining requests, or null when the effective limit is unlimited (`0`). */
    fun remaining(
        context: Context,
        isAuthorPlus: Boolean,
        isAuthorPro: Boolean = false,
    ): Int? {
        val limit = effectiveLimit(isAuthorPlus, isAuthorPro)
        if (limit <= 0) return null
        return (limit - usageCount(context, isAuthorPlus, isAuthorPro)).coerceAtLeast(0)
    }

    fun isExhausted(
        context: Context,
        isAuthorPlus: Boolean,
        isAuthorPro: Boolean = false,
    ): Boolean {
        val limit = effectiveLimit(isAuthorPlus, isAuthorPro)
        if (limit <= 0) return false
        return usageCount(context, isAuthorPlus, isAuthorPro) >= limit
    }

    fun checkAndIncrement(
        context: Context,
        isAuthorPlus: Boolean = false,
        isAuthorPro: Boolean = false,
    ) {
        val limit = effectiveLimit(isAuthorPlus, isAuthorPro)
        if (limit <= 0) return

        if (!isAuthorPlus && !isAuthorPro) {
            val key = accountKey(context)
            val count = lifetimeCount(context, key)
            if (count >= limit) {
                throw MirrorAIError.RateLimited(limit, isAuthorPlus = false, isAuthorPro = false)
            }
            setLifetimeCount(context, key, count + 1)
            return
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = dayStamp()
        val storedDay = prefs.getString(KEY_DAY, null)
        var count = prefs.getInt(KEY_COUNT, 0)
        if (storedDay != today) {
            count = 0
            prefs.edit().putString(KEY_DAY, today).apply()
        }
        if (count >= limit) {
            throw MirrorAIError.RateLimited(
                limit,
                isAuthorPlus = isAuthorPlus && !isAuthorPro,
                isAuthorPro = isAuthorPro,
            )
        }
        prefs.edit().putInt(KEY_COUNT, count + 1).apply()
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
