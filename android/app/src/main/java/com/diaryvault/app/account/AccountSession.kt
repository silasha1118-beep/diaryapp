package com.diaryvault.app.account

import android.content.Context

/**
 * Local account session stub.
 *
 * iOS ships Sign in with Apple + a real gate in this PR.
 * Android keeps SharedPreferences hooks only — no Credential Manager / Google Sign-In
 * until the Play Store path is prioritized (near-term target still runs without a Mac,
 * but full auth/billing is iOS-first).
 */
class AccountSession(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    enum class Provider(val raw: String) {
        LOCAL("local"),
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

    var provider: Provider?
        get() = Provider.fromRaw(prefs.getString(KEY_PROVIDER, null))
        private set(value) {
            prefs.edit().putString(KEY_PROVIDER, value?.raw).apply()
        }

    val isSignedIn: Boolean get() = !userId.isNullOrBlank()

    /** Stub local session for Settings / future gate — not a production auth path. */
    fun signInLocally(displayName: String?) {
        userId = "local-" + java.util.UUID.randomUUID()
        this.displayName = displayName?.trim()?.ifEmpty { null }
        provider = Provider.LOCAL
    }

    fun signOut() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_PROVIDER)
            .apply()
    }

    companion object {
        private const val PREFS = "diary_vault_account"
        private const val KEY_USER_ID = "userId"
        private const val KEY_DISPLAY_NAME = "displayName"
        private const val KEY_PROVIDER = "provider"
    }
}
