package com.diaryvault.app.account

import android.content.Context

/**
 * Local account session stub.
 *
 * iOS ships phone SMS OTP (stub) + optional Apple as the account gate.
 * Android keeps SharedPreferences hooks only — no real SMS SDK / Credential Manager
 * until the Play Store path is prioritized (near-term target still runs without a Mac,
 * but full auth/billing is iOS-first). Primary provider semantics match iOS: **phone**.
 */
class AccountSession(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    enum class Provider(val raw: String) {
        PHONE("phone"),
        LOCAL("local"),
        /** Reserved; not a primary path (aligned with iOS — WeChat/Google dropped as primary). */
        GOOGLE("google"),
        ;

        companion object {
            fun fromRaw(raw: String?): Provider? = entries.firstOrNull { it.raw == raw }
        }
    }

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        private set(value) {
            prefs.edit().putString(KEY_USER_ID, value).apply()
        }

    var displayName: String?
        get() = prefs.getString(KEY_DISPLAY_NAME, null)
        private set(value) {
            prefs.edit().putString(KEY_DISPLAY_NAME, value).apply()
        }

    var phoneE164: String?
        get() = prefs.getString(KEY_PHONE, null)
        private set(value) {
            prefs.edit().putString(KEY_PHONE, value).apply()
        }

    var provider: Provider?
        get() = Provider.fromRaw(prefs.getString(KEY_PROVIDER, null))
        private set(value) {
            prefs.edit().putString(KEY_PROVIDER, value?.raw).apply()
        }

    val isSignedIn: Boolean get() = !userId.isNullOrBlank()

    /** Stub phone session — mirrors iOS SMS OTP success without sending SMS. */
    fun signInWithPhone(phoneE164: String, displayName: String?) {
        val digits = phoneE164.filter { it.isDigit() }
        userId = "phone-$digits"
        this.phoneE164 = phoneE164.trim().ifEmpty { null }
        this.displayName = displayName?.trim()?.ifEmpty { null }
        provider = Provider.PHONE
    }

    /** Stub local session for Settings / future gate — not a production auth path. */
    fun signInLocally(displayName: String?) {
        userId = "local-" + java.util.UUID.randomUUID()
        this.displayName = displayName?.trim()?.ifEmpty { null }
        phoneE164 = null
        provider = Provider.LOCAL
    }

    fun signOut() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_PHONE)
            .remove(KEY_PROVIDER)
            .apply()
    }

    companion object {
        private const val PREFS = "diary_vault_account"
        private const val KEY_USER_ID = "userId"
        private const val KEY_DISPLAY_NAME = "displayName"
        private const val KEY_PHONE = "phoneE164"
        private const val KEY_PROVIDER = "provider"
    }
}
