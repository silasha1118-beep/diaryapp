package com.diaryvault.app

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.diaryvault.app.account.AccountSession
import com.diaryvault.app.auth.DebugPhoneTestCodes
import com.diaryvault.app.auth.PhoneNumberNormalizer
import com.diaryvault.app.auth.SMSAuthError
import com.diaryvault.app.auth.SMSAuthProviderFactory
import com.diaryvault.app.auth.SMSAuthProviding
import com.diaryvault.app.auth.SMSAuthUser
import com.diaryvault.app.auth.SMSCodeSession
import com.diaryvault.app.billing.BillingConfig
import com.diaryvault.app.billing.SubscriptionStore
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
import com.diaryvault.app.service.MirrorUsageGuard
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
    private val accountSession = AccountSession(application)
    val subscriptionStore = SubscriptionStore(application)
    private val isAuthorPlusEntitled: Boolean
        get() = subscriptionStore.isSubscribed.value && !isAuthorProEntitled
    private val isAuthorProEntitled: Boolean
        get() = subscriptionStore.isAuthorPro.value
    private val ai = createMirrorAIService(
        application,
        isAuthorPlus = { isAuthorPlusEntitled },
        isAuthorPro = { isAuthorProEntitled },
    )
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

    /** Soft-deleted in the last 30 days — Settings restore list (hidden from Vault). */
    val recentlyDeletedEntries: StateFlow<List<DiaryEntry>> = store.entries
        .map { list ->
            val cutoff = System.currentTimeMillis() - DiaryStore.RESTORE_WINDOW_MS
            list.filter { entry ->
                entry.deleted && (entry.deletedAt ?: entry.updatedAt) >= cutoff
            }.sortedByDescending { it.deletedAt ?: it.updatedAt }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.recentlyDeleted())

    private val _mirrorMessages = MutableStateFlow<List<MirrorMessage>>(emptyList())
    val mirrorMessages: StateFlow<List<MirrorMessage>> = _mirrorMessages.asStateFlow()

    private val _mirrorThinking = MutableStateFlow(false)
    val mirrorThinking: StateFlow<Boolean> = _mirrorThinking.asStateFlow()

    /** Clear banner error (network / not configured / quota) — not appended as a chat bubble. */
    private val _mirrorError = MutableStateFlow<String?>(null)
    val mirrorError: StateFlow<String?> = _mirrorError.asStateFlow()

    private val _mirrorQuotaTick = MutableStateFlow(0)
    val mirrorQuotaTick: StateFlow<Int> = _mirrorQuotaTick.asStateFlow()

    val useCloudClient: Boolean get() = AIConfig.useCloudClient
    val isAnthropicProvider: Boolean get() = AIConfig.provider == AIProvider.ANTHROPIC
    val aiModelLabel: String get() = if (useCloudClient) AIConfig.model else "mock"
    val dataResidency: String get() = AIConfig.dataResidencyLabel

    private val _accountSignedIn = MutableStateFlow(accountSession.isSignedIn)
    val accountSignedIn: StateFlow<Boolean> = _accountSignedIn.asStateFlow()

    private val _accountLabel = MutableStateFlow(accountSession.displayLabel)
    val accountLabel: StateFlow<String> = _accountLabel.asStateFlow()

    private val _hasLiveFirebaseAuth = MutableStateFlow(accountSession.hasLiveFirebaseAuth)
    val hasLiveFirebaseAuth: StateFlow<Boolean> = _hasLiveFirebaseAuth.asStateFlow()

    /** True when Author Plus or Author Pro is entitled (Play Billing / DEBUG simulate). */
    val billingActive: StateFlow<Boolean> = subscriptionStore.isSubscribed
    val billingTier: StateFlow<BillingConfig.Tier> = subscriptionStore.tier
    val billingIsAuthorPro: StateFlow<Boolean> = subscriptionStore.isAuthorPro
    val billingActiveProductId: StateFlow<String?> = subscriptionStore.activeProductId
    val billingExpirationMs: StateFlow<Long?> = subscriptionStore.expirationMs

    val lastCloudSyncAt: StateFlow<Long?> = store.lastCloudSyncAt
    val lastCloudSyncError: StateFlow<String?> = store.lastCloudSyncError
    val lastCloudSyncServerCount: StateFlow<Int?> = store.lastCloudSyncServerCount
    val isSyncing: StateFlow<Boolean> = store.isSyncing

    val billingProductIds: List<String> get() = BillingConfig.productIds
    val billingPlanName: String
        get() = when (subscriptionStore.tier.value) {
            BillingConfig.Tier.AUTHOR_PRO -> BillingConfig.HIGHER_PLAN_NAME
            BillingConfig.Tier.AUTHOR_PLUS -> BillingConfig.PLAN_NAME
            BillingConfig.Tier.FREE -> BillingConfig.Tier.FREE.displayName
        }

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
        // Recompute Mirror / Vault quota UI when Play entitlement changes.
        viewModelScope.launch {
            subscriptionStore.tier.collect {
                _mirrorQuotaTick.value = _mirrorQuotaTick.value + 1
            }
        }
    }

    /**
     * Send live SMS OTP. With google-services.json: **DEBUG → Firebase for all dials**
     * (incl. +86 Console test numbers); **release +86 → Aliyun** when keys are set
     * (missing keys / CF → Firebase fallback); other dials → Firebase Phone Auth;
     * otherwise local stub.
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
                android.util.Log.i(
                    "VaultAuth",
                    "sendPhoneCode e164=$e164 resend=$resend dial=$dialLabel " +
                        "providerKind=$smsProviderKind project=${FirebaseBootstrap.projectId} " +
                        "debugAutoSms=${DebugPhoneTestCodes.codeFor(e164) != null}",
                )
                val session = smsProvider.sendCode(activity, e164, resend = resend)
                android.util.Log.i(
                    "VaultAuth",
                    "sendPhoneCode ok path=${session.path} auto=${session.autoVerified} " +
                        "idLen=${session.verificationID.length} e164=${session.phoneE164}",
                )
                if (session.autoVerified) {
                    completePhoneSignIn(smsProvider.verify("", session))
                } else if (session.verificationID.isBlank()) {
                    _authError.value = string(R.string.auth_sms_empty_verification)
                } else {
                    _otpSession.value = session
                }
            } catch (e: Exception) {
                android.util.Log.e("VaultAuth", "sendPhoneCode failed: ${e.message}", e)
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
                val normalized = PhoneNumberNormalizer.normalizeOtpDigits(code)
                android.util.Log.i(
                    "VaultAuth",
                    "verifyPhoneCode path=${session.path} e164=${session.phoneE164} " +
                        "codeLen=${normalized.length} idLen=${session.verificationID.length} " +
                        "auto=${session.autoVerified} providerKind=$smsProviderKind " +
                        "project=${FirebaseBootstrap.projectId}",
                )
                completePhoneSignIn(smsProvider.verify(normalized, session))
                android.util.Log.i("VaultAuth", "verifyPhoneCode ok path=${session.path}")
            } catch (e: Exception) {
                android.util.Log.e("VaultAuth", "verifyPhoneCode failed: ${e.message}", e)
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
        // Play entitlements stay with the Google account — do not clear on app sign-out.
        _accountSignedIn.value = false
        _accountLabel.value = ""
        _hasLiveFirebaseAuth.value = false
        _otpSession.value = null
    }

    private fun refreshAccountState() {
        _accountSignedIn.value = accountSession.isSignedIn
        _accountLabel.value = accountSession.displayLabel
        _hasLiveFirebaseAuth.value = accountSession.hasLiveFirebaseAuth
    }

    private fun resolveE164(phoneRaw: String, dialLabel: String?): String {
        val trimmed = PhoneNumberNormalizer.normalizePhoneInput(phoneRaw.trim())
        if (trimmed.startsWith("+")) return PhoneNumberNormalizer.normalizeToE164(trimmed)
        if (dialLabel != null) {
            val dial = PhoneNumberNormalizer.dialFromLabel(dialLabel)
            return PhoneNumberNormalizer.composeE164(dial, trimmed)
        }
        return PhoneNumberNormalizer.normalizeToE164(trimmed)
    }

    private fun mapAuthError(e: Exception): String = when (e) {
        is SMSAuthError.InvalidPhone -> string(R.string.auth_phone_invalid)
        // DEBUG: include Firebase project + error code so project mismatch / burned
        // verificationId is visible without Logcat (Console test list may already be correct).
        is SMSAuthError.InvalidCode ->
            if (BuildConfig.DEBUG) {
                val project = FirebaseBootstrap.projectId?.takeIf { it.isNotBlank() }
                if (project == null || !FirebaseBootstrap.isConfigured) {
                    // Typical when google-services.json was missing at assemble time (stub OTP)
                    // or resources lack project_id — Console 123456 will never verify.
                    string(R.string.auth_otp_error_debug_missing_google_services)
                } else {
                    string(
                        R.string.auth_otp_error_debug_detail,
                        project,
                        e.firebaseErrorCode.ifBlank { "INVALID_CODE" },
                    )
                }
            } else {
                string(R.string.auth_otp_error)
            }
        is SMSAuthError.SessionExpired ->
            if (BuildConfig.DEBUG && e.firebaseErrorCode.isNotBlank()) {
                string(R.string.auth_otp_session_expired_debug, e.firebaseErrorCode)
            } else {
                string(R.string.auth_otp_session_expired)
            }
        is SMSAuthError.RateLimited -> string(R.string.auth_sms_rate_limited)
        is SMSAuthError.BillingRequired -> string(R.string.auth_sms_billing_required)
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

    fun restoreEntry(id: String) {
        store.restore(id)
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

    fun clearMirrorError() {
        _mirrorError.value = null
    }

    fun mirrorQuotaRemaining(): Int? {
        val ctx = getApplication<Application>()
        return MirrorUsageGuard.remaining(ctx, isAuthorPlusEntitled, isAuthorProEntitled)
    }

    fun mirrorQuotaLimit(): Int =
        MirrorUsageGuard.effectiveLimit(isAuthorPlusEntitled, isAuthorProEntitled)

    fun mirrorQuotaExhausted(): Boolean {
        val ctx = getApplication<Application>()
        return MirrorUsageGuard.isExhausted(ctx, isAuthorPlusEntitled, isAuthorProEntitled)
    }

    fun sendMirror(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _mirrorThinking.value) return

        val ctx = getApplication<Application>()
        val plus = isAuthorPlusEntitled
        val pro = isAuthorProEntitled

        if (MirrorUsageGuard.isExhausted(ctx, plus, pro)) {
            val limit = MirrorUsageGuard.effectiveLimit(plus, pro)
            _mirrorError.value = when {
                pro -> string(R.string.mirror_quota_exhausted_pro, limit)
                plus -> string(R.string.mirror_quota_exhausted_plus, limit)
                else -> string(R.string.mirror_quota_exhausted_free, limit)
            }
            return
        }

        if (!AIConfig.useCloudClient) {
            _mirrorError.value = string(R.string.mirror_error_not_configured)
            return
        }

        val userMsg = MirrorMessage(role = MirrorRole.User, text = trimmed)
        _mirrorMessages.value = _mirrorMessages.value + userMsg
        _mirrorThinking.value = true
        _mirrorError.value = null

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
                _mirrorError.value = when (e) {
                    is MirrorAIError.RateLimited -> when {
                        e.isAuthorPro -> string(R.string.mirror_quota_exhausted_pro, e.limit)
                        e.isAuthorPlus -> string(R.string.mirror_quota_exhausted_plus, e.limit)
                        else -> string(R.string.mirror_quota_exhausted_free, e.limit)
                    }
                    is MirrorAIError.NotConfigured -> string(R.string.mirror_error_not_configured)
                    is MirrorAIError.Network -> string(R.string.mirror_error_network, e.message ?: "")
                    is MirrorAIError.Decoding -> string(R.string.mirror_error_decoding)
                    is MirrorAIError.EmptyInput -> null
                    else -> string(R.string.mirror_error_network, e.message ?: e.toString())
                }
            } finally {
                _mirrorThinking.value = false
                _mirrorQuotaTick.value = _mirrorQuotaTick.value + 1
            }
        }
    }
}
