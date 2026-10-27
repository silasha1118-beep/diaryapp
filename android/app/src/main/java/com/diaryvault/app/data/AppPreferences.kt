package com.diaryvault.app.data

import android.content.Context
import com.diaryvault.app.model.AppLanguage

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("diary_vault_prefs", Context.MODE_PRIVATE)

    var didCompleteOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    var gentleRemindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDERS, true)
        set(value) = prefs.edit().putBoolean(KEY_REMINDERS, value).apply()

    var displayName: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var languageTag: String
        get() = prefs.getString(KEY_LANG, null) ?: AppLanguage.fromTag(
            java.util.Locale.getDefault().toLanguageTag()
        ).tag
        set(value) = prefs.edit().putString(KEY_LANG, value).apply()

    companion object {
        private const val KEY_ONBOARDING = "didCompleteOnboarding"
        private const val KEY_REMINDERS = "gentleRemindersEnabled"
        private const val KEY_NAME = "displayName"
        private const val KEY_LANG = "appLanguage"
    }
}
