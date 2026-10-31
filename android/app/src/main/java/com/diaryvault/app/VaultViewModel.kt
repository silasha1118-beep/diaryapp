package com.diaryvault.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.diaryvault.app.account.AccountSession
import com.diaryvault.app.billing.BillingConfig
import com.diaryvault.app.billing.BillingStub
import com.diaryvault.app.config.AIConfig
import com.diaryvault.app.config.AIProvider
import com.diaryvault.app.config.UploadQuotaConfig
import com.diaryvault.app.data.AppPreferences
import com.diaryvault.app.data.DiaryStore
import com.diaryvault.app.model.AppLanguage
import com.diaryvault.app.model.DiaryEntry
import com.diaryvault.app.model.MirrorContext
import com.diaryvault.app.model.MirrorMessage
import com.diaryvault.app.model.MirrorRole
import com.diaryvault.app.service.DiaryUploadUsageGuard
import com.diaryvault.app.service.MirrorAIError
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

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPreferences(application)
    private val store = DiaryStore(application)
    private val ai = createMirrorAIService(application)
    private val accountSession = AccountSession(application)
    private val billingStub = BillingStub()

    private val _language = MutableStateFlow(AppLanguage.fromTag(prefs.languageTag))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _didCompleteOnboarding = MutableStateFlow(prefs.didCompleteOnboarding)
    val didCompleteOnboarding: StateFlow<Boolean> = _didCompleteOnboarding.asStateFlow()

    private val _displayName = MutableStateFlow(prefs.displayName)
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _gentleReminders = MutableStateFlow(prefs.gentleRemindersEnabled)
    val gentleReminders: StateFlow<Boolean> = _gentleReminders.asStateFlow()

    val entries: StateFlow<List<DiaryEntry>> = store.entries
        .map { list ->
            list.sortedWith(
                compareByDescending<DiaryEntry> { it.createdAt }
                    .thenByDescending { it.updatedAt }
                    .thenByDescending { it.id },
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.sortedEntries)

    private val _mirrorMessages = MutableStateFlow<List<MirrorMessage>>(emptyList())
    val mirrorMessages: StateFlow<List<MirrorMessage>> = _mirrorMessages.asStateFlow()

    private val _mirrorThinking = MutableStateFlow(false)
    val mirrorThinking: StateFlow<Boolean> = _mirrorThinking.asStateFlow()

    val useCloudClient: Boolean get() = AIConfig.useCloudClient
    val isAnthropicProvider: Boolean get() = AIConfig.provider == AIProvider.ANTHROPIC
    val dataResidency: String get() = AIConfig.dataResidencyLabel

    private val _accountSignedIn = MutableStateFlow(accountSession.isSignedIn)
    val accountSignedIn: StateFlow<Boolean> = _accountSignedIn.asStateFlow()

    private val _accountLabel = MutableStateFlow(accountSession.displayName.orEmpty())
    val accountLabel: StateFlow<String> = _accountLabel.asStateFlow()

    private val _billingActive = MutableStateFlow(billingStub.isSubscribed)
    val billingActive: StateFlow<Boolean> = _billingActive.asStateFlow()

    val billingProductIds: List<String> get() = BillingConfig.productIds
    val billingPlanName: String get() = BillingConfig.PLAN_NAME

    /** Stub only — creates a local session; not Google Sign-In. */
    fun signInLocalStub() {
        accountSession.signInLocally(_displayName.value.ifBlank { null })
        _accountSignedIn.value = accountSession.isSignedIn
        _accountLabel.value = accountSession.displayName.orEmpty()
    }

    fun signOutAccount() {
        accountSession.signOut()
        billingStub.clear()
        _accountSignedIn.value = false
        _accountLabel.value = ""
        _billingActive.value = false
    }

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

    /**
     * Result of attempting a new text diary create under daily upload quotas.
     * [shouldNudgePaywall] means Free soft band — UI may show a dismissible Plus reminder then retry.
     */
    sealed class AddEntryResult {
        data object Saved : AddEntryResult()
        data class Blocked(
            val limit: Int,
            val isAuthorPlus: Boolean,
            val isAuthorPro: Boolean,
        ) : AddEntryResult()
        data class SoftPaywall(val used: Int, val limit: Int) : AddEntryResult()
    }

    private val isAuthorPlusEntitled: Boolean
        get() = billingStub.isSubscribed

    /** Play Billing Pro not wired yet — always false until product IDs land. */
    private val isAuthorProEntitled: Boolean
        get() = false

    /**
     * Creates a new text entry if under quota.
     * @param suppressSoftPaywall When true (after dismissible reminder), skip soft nudge and save.
     */
    fun addEntry(
        title: String,
        body: String,
        moodTag: String?,
        suppressSoftPaywall: Boolean = false,
    ): AddEntryResult {
        val ctx = getApplication<Application>()
        val plus = isAuthorPlusEntitled
        val pro = isAuthorProEntitled
        if (DiaryUploadUsageGuard.isExhausted(ctx, UploadQuotaConfig.Kind.TEXT, plus, pro)) {
            val limit = DiaryUploadUsageGuard.effectiveLimit(UploadQuotaConfig.Kind.TEXT, plus, pro)
            return AddEntryResult.Blocked(limit, plus, pro)
        }
        if (!suppressSoftPaywall &&
            DiaryUploadUsageGuard.shouldNudgePaywall(ctx, UploadQuotaConfig.Kind.TEXT, plus, pro)
        ) {
            val used = DiaryUploadUsageGuard.usageCountToday(ctx, UploadQuotaConfig.Kind.TEXT)
            val limit = DiaryUploadUsageGuard.effectiveLimit(UploadQuotaConfig.Kind.TEXT, plus, pro)
            return AddEntryResult.SoftPaywall(used, limit)
        }
        return try {
            DiaryUploadUsageGuard.checkAndIncrement(ctx, UploadQuotaConfig.Kind.TEXT, plus, pro)
            store.add(title, body, moodTag)
            AddEntryResult.Saved
        } catch (e: DiaryUploadUsageGuard.RateLimited) {
            AddEntryResult.Blocked(e.limit, e.isAuthorPlus, e.isAuthorPro)
        }
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
                    conversation = _mirrorMessages.value,
                )
                val reply = withContext(Dispatchers.IO) {
                    ai.reply(trimmed, context) { resId, args ->
                        if (args == null || args.isEmpty()) string(resId)
                        else string(resId, *args)
                    }
                }
                _mirrorMessages.value = _mirrorMessages.value + reply
            } catch (e: Exception) {
                val fallbackText = when (e) {
                    is MirrorAIError.RateLimited, is MirrorAIError.Network, is MirrorAIError.NotConfigured ->
                        e.message ?: e.toString()
                    else ->
                        string(R.string.mirror_mock_no_cite) + "\n\n" + string(R.string.mirror_mock_question)
                }
                val fallback = MirrorMessage(
                    role = MirrorRole.Mirror,
                    text = fallbackText,
                )
                _mirrorMessages.value = _mirrorMessages.value + fallback
            } finally {
                _mirrorThinking.value = false
            }
        }
    }
}
