package com.diaryvault.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.diaryvault.app.config.AIConfig
import com.diaryvault.app.data.AppPreferences
import com.diaryvault.app.data.DiaryStore
import com.diaryvault.app.model.AppLanguage
import com.diaryvault.app.model.DiaryEntry
import com.diaryvault.app.model.MirrorContext
import com.diaryvault.app.model.MirrorMessage
import com.diaryvault.app.model.MirrorRole
import com.diaryvault.app.service.createMirrorAIService
import com.diaryvault.app.service.localeForLanguageTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPreferences(application)
    private val store = DiaryStore(application)
    private val ai = createMirrorAIService()

    private val _language = MutableStateFlow(AppLanguage.fromTag(prefs.languageTag))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _didCompleteOnboarding = MutableStateFlow(prefs.didCompleteOnboarding)
    val didCompleteOnboarding: StateFlow<Boolean> = _didCompleteOnboarding.asStateFlow()

    private val _displayName = MutableStateFlow(prefs.displayName)
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _gentleReminders = MutableStateFlow(prefs.gentleRemindersEnabled)
    val gentleReminders: StateFlow<Boolean> = _gentleReminders.asStateFlow()

    val entries: StateFlow<List<DiaryEntry>> = store.entries
        .map { list -> list.sortedByDescending { it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.sortedEntries)

    private val _mirrorMessages = MutableStateFlow<List<MirrorMessage>>(emptyList())
    val mirrorMessages: StateFlow<List<MirrorMessage>> = _mirrorMessages.asStateFlow()

    private val _mirrorThinking = MutableStateFlow(false)
    val mirrorThinking: StateFlow<Boolean> = _mirrorThinking.asStateFlow()

    val useCloudClient: Boolean get() = AIConfig.useCloudClient
    val dataResidency: String get() = AIConfig.dataResidencyLabel

    fun setLanguage(lang: AppLanguage) {
        prefs.languageTag = lang.tag
        _language.value = lang
    }

    fun completeOnboarding(name: String) {
        val trimmed = name.trim()
        prefs.displayName = trimmed
        prefs.didCompleteOnboarding = true
        _displayName.value = trimmed
        _didCompleteOnboarding.value = true
    }

    fun resetOnboarding() {
        prefs.didCompleteOnboarding = false
        _didCompleteOnboarding.value = false
    }

    fun setGentleReminders(enabled: Boolean) {
        prefs.gentleRemindersEnabled = enabled
        _gentleReminders.value = enabled
    }

    fun setDisplayName(name: String) {
        prefs.displayName = name.trim()
        _displayName.value = name.trim()
    }

    fun entry(id: String): DiaryEntry? = store.entry(id)

    fun addEntry(title: String, body: String, moodTag: String?) {
        store.add(title, body, moodTag)
    }

    fun updateEntry(entry: DiaryEntry) {
        store.update(entry)
    }

    fun deleteEntry(id: String) {
        store.delete(id)
    }

    fun string(resId: Int, vararg args: Any): String {
        val locale = localeForLanguageTag(_language.value.tag)
        val config = getApplication<Application>().resources.configuration
        val localized = android.content.res.Configuration(config).apply {
            setLocale(locale)
        }
        val ctx = getApplication<Application>().createConfigurationContext(localized)
        return if (args.isEmpty()) ctx.getString(resId) else ctx.getString(resId, *args)
    }

    fun sendMirror(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _mirrorThinking.value) return

        val userMsg = MirrorMessage(role = MirrorRole.User, text = trimmed)
        _mirrorMessages.value = _mirrorMessages.value + userMsg
        _mirrorThinking.value = true

        viewModelScope.launch {
            try {
                val context = MirrorContext(
                    recentEntries = store.sortedEntries,
                    userDisplayName = _displayName.value,
                    localeTag = _language.value.tag,
                )
                val reply = withContext(Dispatchers.IO) {
                    ai.reply(trimmed, context) { resId, args ->
                        if (args == null || args.isEmpty()) string(resId)
                        else string(resId, *args)
                    }
                }
                _mirrorMessages.value = _mirrorMessages.value + reply
            } catch (_: Exception) {
                val fallback = MirrorMessage(
                    role = MirrorRole.Mirror,
                    text = string(R.string.mirror_mock_no_cite) + "\n\n" +
                        string(R.string.mirror_mock_question),
                )
                _mirrorMessages.value = _mirrorMessages.value + fallback
            } finally {
                _mirrorThinking.value = false
            }
        }
    }
}
