package com.diaryvault.app.account

import android.content.Context
import com.diaryvault.app.auth.PhoneNumberNormalizer
import com.diaryvault.app.config.FirebaseBootstrap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Account session for phone SMS OTP (primary). When Firebase Auth succeeds, `userId` is the
 * Firebase UID and diary sync can run. Stub / local sessions stay on-device only.
 */
class AccountSession(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var authListener: FirebaseAuth.AuthStateListener? = null

    enum class Provider(val raw: String) {
        PHONE("phone"),
        LOCAL("local"),
        GOOGLE("google"),
        ;

        companion object {
            fun fromRaw(raw: String?): Provider? = entries.firstOrNull { it.raw == raw }
        }
    }

    private val _userId = MutableStateFlow(prefs.getString(KEY_USER_ID, null))
    val userIdFlow: StateFlow<String?> = _userId.asStateFlow()
    var userId: String?
        get() = _userId.value
        private set(value) {
            _userId.value = value
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

    private val _firebaseAuthUID = MutableStateFlow<String?>(null)
    val firebaseAuthUID: StateFlow<String?> = _firebaseAuthUID.asStateFlow()

    private val _firebaseAuthReady = MutableStateFlow(false)
    val firebaseAuthReady: StateFlow<Boolean> = _firebaseAuthReady.asStateFlow()

    private val _hasLinkedPhone = MutableStateFlow(provider == Provider.PHONE || phoneE164 != null)
    val hasLinkedPhone: StateFlow<Boolean> = _hasLinkedPhone.asStateFlow()

    val isSignedIn: Boolean get() = !userId.isNullOrBlank()

    /** Firestore sync requires a live Firebase Auth user — not merely a SharedPreferences / stub session. */
    val hasLiveFirebaseAuth: Boolean
        get() = !_firebaseAuthUID.value.isNullOrBlank()

    val displayLabel: String
        get() {
            displayName?.takeIf { it.isNotBlank() }?.let { return it }
            phoneE164?.takeIf { it.isNotBlank() }?.let {
                return PhoneNumberNormalizer.maskedDisplay(it)
            }
            return ""
        }

    init {
        FirebaseBootstrap.configureIfNeeded(appContext)
        if (FirebaseBootstrap.isConfigured) {
            val auth = FirebaseAuth.getInstance()
            auth.currentUser?.let { applyFirebaseUser(it) }
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                _firebaseAuthReady.value = true
                if (user != null) {
                    applyFirebaseUser(user)
                } else {
                    // First callback can briefly be nil while restore is in flight — do not wipe
                    // SharedPreferences on that alone. Only clear the live-UID mirror.
                    _firebaseAuthUID.value = null
                }
            }
            authListener = listener
            auth.addAuthStateListener(listener)
        } else {
            _firebaseAuthReady.value = true
            _firebaseAuthUID.value = null
        }
    }

    private fun applyFirebaseUser(fbUser: FirebaseUser) {
        _firebaseAuthUID.value = fbUser.uid
        val linkedPhone = userHasPhoneProvider(fbUser)
        _hasLinkedPhone.value = linkedPhone
        val phone = fbUser.phoneNumber ?: if (linkedPhone) phoneE164 else null
        apply(
            userId = fbUser.uid,
            displayName = displayName ?: fbUser.displayName,
            phoneE164 = phone,
            provider = Provider.PHONE,
        )
    }

    fun signInWithPhone(userId: String, phoneE164: String, displayName: String?) {
        apply(
            userId = userId,
            displayName = displayName,
            phoneE164 = phoneE164,
            provider = Provider.PHONE,
        )
        if (FirebaseBootstrap.isConfigured) {
            val current = FirebaseAuth.getInstance().currentUser
            if (current != null && current.uid == userId) {
                _firebaseAuthUID.value = userId
                _hasLinkedPhone.value = userHasPhoneProvider(current)
            } else {
                _firebaseAuthUID.value = null
                _hasLinkedPhone.value = true
            }
        } else {
            _firebaseAuthUID.value = null
            _hasLinkedPhone.value = true
        }
    }

    fun signInLocally(displayName: String?) {
        apply(
            userId = "local-" + java.util.UUID.randomUUID(),
            displayName = displayName,
            phoneE164 = null,
            provider = Provider.LOCAL,
        )
        _firebaseAuthUID.value = null
        _hasLinkedPhone.value = false
    }

    fun signOut() {
        if (FirebaseBootstrap.isConfigured) {
            runCatching { FirebaseAuth.getInstance().signOut() }
        }
        _firebaseAuthUID.value = null
        _hasLinkedPhone.value = false
        clearLocalSession()
    }

    private fun clearLocalSession() {
        userId = null
        displayName = null
        phoneE164 = null
        provider = null
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_PHONE)
            .remove(KEY_PROVIDER)
            .apply()
    }

    private fun apply(
        userId: String,
        displayName: String?,
        phoneE164: String?,
        provider: Provider,
    ) {
        this.userId = userId
        this.displayName = displayName?.trim()?.ifEmpty { null }
        this.phoneE164 = phoneE164?.trim()?.ifEmpty { null }
        this.provider = provider
    }

    companion object {
        private const val PREFS = "diary_vault_account"
        private const val KEY_USER_ID = "userId"
        private const val KEY_DISPLAY_NAME = "displayName"
        private const val KEY_PHONE = "phoneE164"
        private const val KEY_PROVIDER = "provider"

        fun userHasPhoneProvider(user: FirebaseUser): Boolean {
            if (!user.phoneNumber.isNullOrBlank()) return true
            return user.providerData.any { it.providerId == "phone" }
        }
    }
}
