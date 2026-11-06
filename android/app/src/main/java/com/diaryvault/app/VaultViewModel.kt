package com.diaryvault.app

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.diaryvault.app.account.AccountSession
import com.diaryvault.app.auth.PhoneNumberNormalizer
import com.diaryvault.app.auth.SMSAuthError
import com.diaryvault.app.auth.SMSAuthProviderFactory
import com.diaryvault.app.auth.SMSAuthProviding
import com.diaryvault.app.auth.SMSAuthUser
import com.diaryvault.app.auth.SMSCodeSession
import com.diaryvault.app.billing.BillingConfig
import com.diaryvault.app.billing.BillingStub
import com.diaryvault.app.config.AIConfig
import com.diaryvault.app.config.AIProvider
import com.diaryvault.app.config.AuthConfig
import com.diaryvault.app.config.FirebaseBootstrap
import com.diaryvault.app.config.UploadQuotaConfig
import com.diaryvault.app.data.AppPreferences
import com.diaryvault.app.data.DiaryStore
import com.diaryvault.app.model.AppLanguage
import com.diaryvault.app.model.DiaryEntry
import com.diaryvault.app.model.DiaryMediaAttachment
import com.diaryvault.app.model.MirrorContext
import com.diaryvault.app.model.MirrorMessage
import com.diaryvault.app.model.MirrorRole
import com.diaryvault.app.service.DiaryUploadUsageGuard
import com.diaryvault.app.service.MirrorAIError
import com.diaryvault.app.service.createMirrorAIService
import com.diaryvault.app.service.localeForLanguageTag
import com.diaryvault.app.sync.DiaryCloudSync
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
    private val store = DiaryStore(application, viewModelScope)
    private val ai = createMirrorAIService(application)
    private val accountSession = AccountSession(application)
    private val billingStub = BillingStub()
    private val smsProvider: SMSAuthProviding = SMSAuthProviderFactory.make(application)

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
            list.filter { !it.deleted }
                .sortedWith(DiaryCloudSync.chronologicalDescending)
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

    private val _accountLabel = MutableStateFlow(accountSession.displayLabel)
    val accountLabel: StateFlow<String> = _accountLabel.asStateFlow()

    private val _hasLiveFirebaseAuth = MutableStateFlow(accountSession.hasLiveFirebaseAuth)
    val hasLiveFirebaseAuth: StateFlow<Boolean> = _hasLiveFirebaseAuth.asStateFlow()

    private val _billingActive = MutableStateFlow(billingStub.isSubscribed)
    val billingActive: StateFlow<Boolean> = _billingActive.asStateFlow()

    val lastCloudSyncAt: StateFlow<Long?> = store.lastCloudSyncAt
    val lastCloudSyncError: StateFlow<String?> = store.lastCloudSyncError
    val lastCloudSyncServerCount: StateFlow<Int?> = store.lastCloudSyncServerCount
    val isSyncing: StateFlow<Boolean> = store.isSyncing

    val billingProductIds: List<String> get() = BillingConfig.productIds
    val billingPlanName: String get() = BillingConfig.PLAN_NAME

    val smsProviderKind: AuthConfig.SMSProviderKind get() = AuthConfig.smsProvider
    val firebaseProjectLabel: String
        get() = store.lastCloudSyncProjectID.value
            ?: DiaryCloudSync.firebaseProjectID
            ?: AuthConfig.firebaseProjectID
            ?: "—"

    private val _otpSession = MutableStateFlow<SMSCodeSession?>(null)
    val otpSession: StateFlow<SMSCodeSession?> = _otpSession.asStateFlow()

    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    init {
        FirebaseBootstrap.configureIfNeeded(application)
        refreshAccountState()
        store.bindCloudSync()
        viewModelScope.launch {
            accountSession.firebaseAuthUID.collect { uid ->
                _hasLiveFirebaseAuth.value = !uid.isNullOrBlank()
                refreshAccountState()
                if (!uid.isNullOrBlank()) {
                    store.scheduleSynchronize()
                }
            }
        }
        viewModelScope.launch {
            accountSession.userIdFlow.collect {
                refreshAccountState()
            }
        }
    }

    /**
     * Send live SMS OTP. With google-services.json: **DEBUG / missing Aliyun → Firebase**
     * (incl. +86 Console test numbers); **release +86 → Aliyun** when keys are set;
     * other dials → Firebase Phone Auth; otherwise local stub.
     * Must keep [activity] on the main path — Firebase Phone Auth needs it for Play Integrity / reCAPTCHA.
     *
     * When Firebase auto-verifies (no SMS), signs in immediately instead of showing an OTP field
     * that would wait forever for a text that never arrives.
     */
    fun sendPhoneCode(
        activity: Activity,
        phoneRaw: String,
        dialLabel: String? = null,
        resend: Boolean = false,
    ) {
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = null
            try {
                val e164 = resolveE164(phoneRaw, dialLabel)
                // Do NOT hop to Dispatchers.IO: Firebase PhoneAuthClient launches
                // Play Integrity / reCAPTCHA via the Activity and must stay on Main.
                val session = smsProvider.sendCode(activity, e164, resend = resend)
                if (session.autoVerified) {
                    completePhoneSignIn(smsProvider.verify("", session))
                } else if (session.verificationID.isBlank()) {
                    _authError.value = string(R.string.auth_sms_empty_verification)
                } else {
                    _otpSession.value = session
                }
            } catch (e: Exception) {
                _authError.value = mapAuthError(e)
            } finally {
                _authBusy.value = false
            }
        }
    }

    fun verifyPhoneCode(code: String) {
        val session = _otpSession.value
        if (session == null) {
            _authError.value = string(R.string.auth_otp_error)
            return
        }
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = null
            try {
                completePhoneSignIn(smsProvider.verify(code, session))
            } catch (e: Exception) {
                _authError.value = mapAuthError(e)
            } finally {
                _authBusy.value = false
            }
        }
    }

    private fun completePhoneSignIn(user: SMSAuthUser) {
        accountSession.signInWithPhone(
            userId = user.userID,
            phoneE164 = user.phoneE164,
            displayName = _displayName.value.ifBlank { null },
        )
        _otpSession.value = null
        refreshAccountState()
        store.scheduleSynchronize()
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun resetOtpSession() {
        smsProvider.clearSendState()
        _otpSession.value = null
        _authError.value = null
    }

    fun syncNow() {
        store.scheduleSynchronize()
    }

    fun signOutAccount() {
        accountSession.signOut()
        billingStub.clear()
        _accountSignedIn.value = false
        _accountLabel.value = ""
        _hasLiveFirebaseAuth.value = false
        _billingActive.value = false
        _otpSession.value = null
    }

    private fun refreshAccountState() {
        _accountSignedIn.value = accountSession.isSignedIn
        _accountLabel.value = accountSession.displayLabel
        _hasLiveFirebaseAuth.value = accountSession.hasLiveFirebaseAuth
    }

    private fun resolveE164(phoneRaw: String, dialLabel: String?): String {
        val trimmed = phoneRaw.trim()
        if (trimmed.startsWith("+")) return PhoneNumberNormalizer.normalizeToE164(trimmed)
        if (dialLabel != null) {
            val dial = PhoneNumberNormalizer.dialFromLabel(dialLabel)
            return PhoneNumberNormalizer.composeE164(dial, trimmed)
        }
        val digits = trimmed.filter { it.isDigit() }
        return "+$digits"
    }

    private fun mapAuthError(e: Exception): String = when (e) {
        is SMSAuthError.InvalidPhone -> string(R.string.auth_phone_invalid)
        is SMSAuthError.InvalidCode -> string(R.string.auth_otp_error)
        is SMSAuthError.RateLimited -> string(R.string.auth_sms_rate_limited)
        is SMSAuthError.RegionNotAllowed -> string(R.string.auth_sms_region_blocked)
        is SMSAuthError.AppVerificationFailed -> string(R.string.auth_sms_app_verification)
        is SMSAuthError.RecaptchaUnavailable -> string(R.string.auth_sms_recaptcha_unavailable)
        is SMSAuthError.RecaptchaBrowserFailed -> string(R.string.auth_sms_recaptcha_browser_failed)
        is SMSAuthError.TimedOut -> string(R.string.auth_sms_timed_out)
        is SMSAuthError.EmptyVerificationId -> string(R.string.auth_sms_empty_verification)
        is SMSAuthError.NotConfigured -> e.detail.ifBlank { string(R.string.auth_sms_generic_failure) }
        // Prefer Firebase’s own message when present — never Logcat tips.
        is SMSAuthError.Underlying -> e.detail.ifBlank { string(R.string.auth_sms_generic_failure) }
        is SMSAuthError -> e.message?.takeIf { it.isNotBlank() } ?: string(R.string.auth_sms_generic_failure)
        else -> e.message?.takeIf { it.isNotBlank() } ?: string(R.string.auth_sms_generic_failure)
    }

    val accountPhone: String? get() = accountSession.phoneE164
    val accountProviderLabel: String
        get() = when (accountSession.provider) {
            AccountSession.Provider.PHONE -> string(R.string.settings_account_provider_phone)
            AccountSession.Provider.LOCAL -> string(R.string.settings_account_provider_local)
            AccountSession.Provider.GOOGLE -> string(R.string.settings_account_provider_local)
            null -> "—"
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

    sealed class AddEntryResult {
        data object Saved : AddEntryResult()
        data class Blocked(
            val limit: Int,
            val isAuthorPlus: Boolean,
            val isAuthorPro: Boolean,
        ) : AddEntryResult()
    }

    private val isAuthorPlusEntitled: Boolean
        get() = billingStub.isSubscribed

    private val isAuthorProEntitled: Boolean
        get() = false

    fun addEntry(
        title: String,
        body: String,
        moodTag: String?,
        attachments: List<DiaryMediaAttachment> = emptyList(),
        entryId: String? = null,
    ): AddEntryResult {
        val ctx = getApplication<Application>()
        val plus = isAuthorPlusEntitled
        val pro = isAuthorProEntitled
        val kind = if (attachments.isNotEmpty()) {
            UploadQuotaConfig.Kind.MULTIMEDIA
        } else {
            UploadQuotaConfig.Kind.TEXT
        }
        if (DiaryUploadUsageGuard.isExhausted(ctx, kind, plus, pro)) {
            val limit = DiaryUploadUsageGuard.effectiveLimit(kind, plus, pro)
            return AddEntryResult.Blocked(limit, plus, pro)
        }
        return try {
            DiaryUploadUsageGuard.checkAndIncrement(ctx, kind, plus, pro)
            store.add(title, body, moodTag, attachments = attachments, entryId = entryId)
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
