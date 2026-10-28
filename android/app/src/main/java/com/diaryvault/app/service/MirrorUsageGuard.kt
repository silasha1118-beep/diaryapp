package com.diaryvault.app.service

import android.content.Context
import com.diaryvault.app.config.AIConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Local daily Mirror AI request counter (not a server-side quota).
 * Free vs Author Plus limits come from [AIConfig]; pass [isAuthorPlus] from billing entitlement
 * (Play Billing stub today — iOS StoreKit is the first wired path).
 */
object MirrorUsageGuard {
    private const val PREFS = "diary_vault_ai_usage"
    private const val KEY_DAY = "usageDay"
    private const val KEY_COUNT = "usageCount"

    fun effectiveLimit(isAuthorPlus: Boolean): Int {
        return if (isAuthorPlus) {
            AIConfig.dailyRequestLimit
        } else {
            AIConfig.freeDailyRequestLimit
        }
    }

    fun usageCountToday(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = dayStamp()
        if (prefs.getString(KEY_DAY, null) != today) return 0
        return prefs.getInt(KEY_COUNT, 0).coerceAtLeast(0)
    }

    fun checkAndIncrement(context: Context, isAuthorPlus: Boolean = false) {
        val limit = effectiveLimit(isAuthorPlus)
        if (limit <= 0) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = dayStamp()
        val storedDay = prefs.getString(KEY_DAY, null)
        var count = prefs.getInt(KEY_COUNT, 0)
        if (storedDay != today) {
            count = 0
            prefs.edit().putString(KEY_DAY, today).apply()
        }
        if (count >= limit) {
            throw MirrorAIError.RateLimited(limit, isAuthorPlus)
        }
        prefs.edit().putInt(KEY_COUNT, count + 1).apply()
    }

    private fun dayStamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(Date())
    }
}
