package com.diaryvault.app.auth

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.diaryvault.app.BuildConfig
import com.diaryvault.app.config.AuthConfig
import com.diaryvault.app.config.FirebaseBootstrap
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Result of requesting an SMS OTP. */
data class SMSCodeSession(
    val verificationID: String,
    val phoneE164: String,
    /** Stub-only hint for DEBUG UI; always null for Firebase. */
    val stubDebugHint: String? = null,
    /**
     * True when Firebase completed verification without an SMS (Play instant / auto-retrieval).
     * UI must sign in immediately — do **not** wait for an SMS that will never arrive.
     */
    val autoVerified: Boolean = false,
)

/** Signed-in identity after a successful OTP verify. */
data class SMSAuthUser(
    val userID: String,
    val phoneE164: String,
)

sealed class SMSAuthError(message: String) : Exception(message) {
    data object InvalidPhone : SMSAuthError("Invalid phone number")
    data object InvalidCode : SMSAuthError("Invalid verification code")
    data class NotConfigured(val detail: String) : SMSAuthError(detail)
    data object RateLimited : SMSAuthError("Too many attempts; try again later")
    data object RegionNotAllowed : SMSAuthError("SMS is not allowed for this phone region")
    data object AppVerificationFailed : SMSAuthError(
        "App verification failed (Play Integrity / reCAPTCHA).",
    )
    data object TimedOut : SMSAuthError("SMS request timed out. Check network, then try again.")
    data object EmptyVerificationId : SMSAuthError(
        "Verification did not complete — no verification id from Firebase.",
    )
    data class Underlying(val detail: String) : SMSAuthError(detail)
}

interface SMSAuthProviding {
    /**
     * Request a live SMS OTP. [activity] must be a foreground Activity so Firebase can
     * complete Play Integrity / reCAPTCHA (do **not** call from a background-only context).
     *
     * @param resend when true, uses the stored [ForceResendingToken] for the same E.164 only.
     */
    suspend fun sendCode(
        activity: Activity,
        phoneE164: String,
        resend: Boolean = false,
    ): SMSCodeSession

    suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser

    /** Drop force-resend token / pending auto credential (e.g. user changed number). */
    fun clearSendState() {}
}

object SMSAuthProviderFactory {
    fun make(context: Context): SMSAuthProviding {
        FirebaseBootstrap.configureIfNeeded(context)
        val resolution = AuthConfig.smsProviderResolution
        val kind = resolution.kind
        // Never silently stay on stub when google-services was baked into this APK.
        if (kind == AuthConfig.SMSProviderKind.STUB && FirebaseBootstrap.hasGoogleServicesJson) {
            val explicitStub = resolution is AuthConfig.SMSProviderResolution.Explicit &&
                resolution.provider == AuthConfig.SMSProviderKind.STUB
            if (!explicitStub) {
                Log.e(
                    "SMSAuthFactory",
                    "google-services.json present but resolution chose stub — forcing Firebase",
                )
                return FirebasePhoneSMSAuthProvider(context.applicationContext)
            }
            Log.w(
                "SMSAuthFactory",
                "DIARY_SMS_PROVIDER=stub with google-services.json present — local OTP only, no live SMS",
            )
        }
        return when (kind) {
            AuthConfig.SMSProviderKind.FIREBASE -> FirebasePhoneSMSAuthProvider(context.applicationContext)
            AuthConfig.SMSProviderKind.TWILIO -> TwilioReadySMSAuthProvider()
            AuthConfig.SMSProviderKind.STUB -> StubSMSAuthProvider()
        }
    }
}

/** Local OTP stub — does **not** send SMS. */
class StubSMSAuthProvider : SMSAuthProviding {
    private val issued = mutableMapOf<String, String>()

    override suspend fun sendCode(
        activity: Activity,
        phoneE164: String,
        resend: Boolean,
    ): SMSCodeSession {
        val normalized = PhoneNumberNormalizer.normalizeToE164(phoneE164)
        if (!PhoneNumberNormalizer.isPlausibleE164(normalized)) throw SMSAuthError.InvalidPhone
        val verificationID = "stub-" + UUID.randomUUID()
        val expected = AuthConfig.stubAcceptCode ?: "%06d".format((0..999_999).random())
        issued[verificationID] = expected
        return SMSCodeSession(
            verificationID = verificationID,
            phoneE164 = normalized,
            stubDebugHint = expected,
        )
    }

    override suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser {
        val trimmed = code.trim()
        if (trimmed.length != 6 || !trimmed.all { it.isDigit() }) throw SMSAuthError.InvalidCode
        val fixed = AuthConfig.stubAcceptCode
        if (fixed != null) {
            if (trimmed != fixed) throw SMSAuthError.InvalidCode
        } else {
            val expected = issued[session.verificationID]
            if (expected != null && trimmed != expected) throw SMSAuthError.InvalidCode
        }
        return SMSAuthUser(
            userID = "phone-" + session.phoneE164.filter { it.isDigit() },
            phoneE164 = session.phoneE164,
        )
    }
}

/**
 * Real Firebase Phone Auth: send SMS OTP → verify code → `signInWithCredential`.
 *
 * Requires:
 * - `android/app/google-services.json` (gitignored)
 * - Phone provider enabled + SMS region allowlist
 * - SHA-1 / SHA-256 of the signing key (Play Integrity / reCAPTCHA)
 * - Foreground Activity (Custom Tabs for reCAPTCHA when Integrity cannot attest a sideloaded APK)
 */
class FirebasePhoneSMSAuthProvider(
    private val appContext: Context,
) : SMSAuthProviding {
    private val forceResendToken = AtomicReference<ForceResendingToken?>(null)
    private val lastPhoneE164 = AtomicReference<String?>(null)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun clearSendState() {
        forceResendToken.set(null)
        lastPhoneE164.set(null)
        pendingAutoCredential.set(null)
    }

    override suspend fun sendCode(
        activity: Activity,
        phoneE164: String,
        resend: Boolean,
    ): SMSCodeSession {
        val normalized = PhoneNumberNormalizer.normalizeToE164(phoneE164)
        if (!PhoneNumberNormalizer.isPlausibleE164(normalized)) throw SMSAuthError.InvalidPhone

        FirebaseBootstrap.configureIfNeeded(appContext)
        if (!FirebaseBootstrap.hasGoogleServicesJson || !FirebaseBootstrap.isConfigured) {
            throw SMSAuthError.NotConfigured(
                "google-services.json missing. Place it at android/app/google-services.json (gitignored), rebuild.",
            )
        }

        if (activity.isFinishing || activity.isDestroyed) {
            throw SMSAuthError.Underlying("Activity is not available for Phone Auth verification.")
        }

        applySMSLanguageCode()
        applyDebugRecaptchaPreference()

        Log.i(
            TAG,
            "verifyPhoneNumber start e164=$normalized resend=$resend " +
                "pkg=${appContext.packageName} activity=${activity.javaClass.simpleName} " +
                "project=${FirebaseBootstrap.projectId}",
        )

        // Phone Auth + reCAPTCHA / Play Integrity must start on the main thread with a live Activity.
        return try {
            withTimeout(SEND_TIMEOUT_MS) {
                withContext(Dispatchers.Main.immediate) {
                    awaitVerification(activity, normalized, resend)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "verifyPhoneNumber timed out for $normalized (reCAPTCHA may have been dismissed)")
            throw SMSAuthError.TimedOut
        }
    }

    /**
     * Sideloaded debug APKs are not Play-recognized — Play Integrity fails and Firebase must
     * fall back to the browser reCAPTCHA. Forcing that flow in DEBUG makes the sheet visible
     * so a silent Integrity miss cannot look like a successful send.
     */
    private fun applyDebugRecaptchaPreference() {
        if (!BuildConfig.DEBUG) return
        try {
            FirebaseAuth.getInstance().firebaseAuthSettings
                .forceRecaptchaFlowForTesting(true)
            Log.i(TAG, "DEBUG: forceRecaptchaFlowForTesting(true) — complete the browser check if shown")
        } catch (e: Exception) {
            Log.w(TAG, "forceRecaptchaFlowForTesting unavailable", e)
        }
    }

    private suspend fun awaitVerification(
        activity: Activity,
        normalized: String,
        resend: Boolean,
    ): SMSCodeSession =
        suspendCancellableCoroutine { cont ->
            val resumed = AtomicBoolean(false)
            fun resumeOnce(block: () -> Unit) {
                if (resumed.compareAndSet(false, true) && cont.isActive) block()
            }

            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Instant verification / SMS Retriever — no SMS for the user to type.
                    val smsPresent = credential.smsCode != null
                    Log.i(TAG, "onVerificationCompleted auto smsCodePresent=$smsPresent")
                    pendingAutoCredential.set(credential)
                    lastPhoneE164.set(normalized)
                    resumeOnce {
                        cont.resume(
                            SMSCodeSession(
                                verificationID = "auto",
                                phoneE164 = normalized,
                                stubDebugHint = null,
                                autoVerified = true,
                            ),
                        )
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e(
                        TAG,
                        "onVerificationFailed code=${(e as? FirebaseAuthException)?.errorCode} msg=${e.message}",
                        e,
                    )
                    resumeOnce { cont.resumeWithException(mapFirebaseError(e)) }
                }

                override fun onCodeSent(verificationId: String, token: ForceResendingToken) {
                    // Treat blank id as failure — never advance OTP UI on an empty session.
                    if (verificationId.isBlank()) {
                        Log.e(TAG, "onCodeSent with blank verificationId — failing closed")
                        resumeOnce {
                            cont.resumeWithException(SMSAuthError.EmptyVerificationId)
                        }
                        return
                    }
                    Log.i(
                        TAG,
                        "onCodeSent verificationIdLen=${verificationId.length} " +
                            "prefix=${verificationId.take(6)}…",
                    )
                    forceResendToken.set(token)
                    lastPhoneE164.set(normalized)
                    prefs().edit().putString(KEY_VERIFICATION_ID, verificationId).apply()
                    resumeOnce {
                        cont.resume(
                            SMSCodeSession(
                                verificationID = verificationId,
                                phoneE164 = normalized,
                                stubDebugHint = null,
                                autoVerified = false,
                            ),
                        )
                    }
                }

                override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
                    // SMS may still arrive; session already resumed via onCodeSent when applicable.
                    Log.d(TAG, "onCodeAutoRetrievalTimeOut verificationIdLen=${verificationId.length}")
                    if (verificationId.isNotBlank()) {
                        prefs().edit().putString(KEY_VERIFICATION_ID, verificationId).apply()
                    }
                }
            }

            val builder = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                .setPhoneNumber(normalized)
                // Auto-retrieval window (not the coroutine wall-clock timeout).
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)

            val storedToken = forceResendToken.get()
            val samePhone = lastPhoneE164.get() == normalized
            when {
                resend && storedToken != null && samePhone -> {
                    builder.setForceResendingToken(storedToken)
                    Log.i(TAG, "applying forceResendingToken for resend")
                }
                resend && (storedToken == null || !samePhone) -> {
                    // Fall through as a fresh send rather than failing closed.
                    Log.w(TAG, "resend requested without usable token — fresh verifyPhoneNumber")
                    forceResendToken.set(null)
                }
                !resend -> {
                    // Never reuse a stale token on a fresh Send (or after “change number”).
                    if (!samePhone) forceResendToken.set(null)
                    // Explicit fresh send: do not attach forceResendingToken.
                }
            }

            val options = builder.build()
            val start: () -> Unit = {
                try {
                    PhoneAuthProvider.verifyPhoneNumber(options)
                } catch (e: Exception) {
                    Log.e(TAG, "verifyPhoneNumber threw", e)
                    resumeOnce { cont.resumeWithException(mapFirebaseError(e)) }
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                start()
            } else {
                mainHandler.post { start() }
            }

            cont.invokeOnCancellation {
                Log.w(TAG, "sendCode cancelled for $normalized")
            }
        }

    override suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser {
        FirebaseBootstrap.configureIfNeeded(appContext)
        if (!FirebaseBootstrap.isConfigured) {
            throw SMSAuthError.NotConfigured(
                "Firebase is not configured. Add google-services.json to android/app/.",
            )
        }

        pendingAutoCredential.getAndSet(null)?.let { auto ->
            Log.i(TAG, "verify via pending auto credential")
            return signIn(auto, session.phoneE164)
        }

        if (session.autoVerified) {
            throw SMSAuthError.Underlying("Auto-verification expired. Request a new code.")
        }

        val trimmed = code.trim()
        if (trimmed.length < 4 || !trimmed.all { it.isDigit() }) throw SMSAuthError.InvalidCode

        val verificationID = session.verificationID.ifEmpty {
            prefs().getString(KEY_VERIFICATION_ID, "").orEmpty()
        }
        if (verificationID.isEmpty() || verificationID == "auto") {
            throw SMSAuthError.Underlying("Missing verification session. Request a new code.")
        }

        val credential = PhoneAuthProvider.getCredential(verificationID, trimmed)
        return try {
            val user = signIn(credential, session.phoneE164)
            prefs().edit().remove(KEY_VERIFICATION_ID).apply()
            user
        } catch (e: Exception) {
            Log.e(TAG, "signInWithCredential failed: ${e.message}", e)
            throw mapFirebaseError(e)
        }
    }

    private suspend fun signIn(credential: PhoneAuthCredential, fallbackPhone: String): SMSAuthUser {
        val result = FirebaseAuth.getInstance().signInWithCredential(credential).await()
        val user = result.user ?: throw SMSAuthError.Underlying("Firebase Auth returned no user")
        return SMSAuthUser(
            userID = user.uid,
            phoneE164 = user.phoneNumber ?: fallbackPhone,
        )
    }

    private fun applySMSLanguageCode() {
        val stored = appContext.getSharedPreferences("diary_vault_prefs", Context.MODE_PRIVATE)
            .getString("appLanguage", "")
            .orEmpty()
        val lang = when (stored) {
            "zh-Hant" -> "zh-TW"
            "zh-Hans" -> "zh-CN"
            "ja" -> "ja"
            "th" -> "th"
            else -> "en"
        }
        FirebaseAuth.getInstance().setLanguageCode(lang)
    }

    private fun prefs() =
        appContext.getSharedPreferences("diary.firebase.auth", Context.MODE_PRIVATE)

    private fun mapFirebaseError(error: Exception): SMSAuthError {
        val message = error.message.orEmpty()
        val lowered = message.lowercase()
        val code = when (error) {
            is FirebaseAuthException -> error.errorCode
            else -> ""
        }

        if (error is FirebaseTooManyRequestsException) return SMSAuthError.RateLimited
        if (error is FirebaseAuthInvalidCredentialsException) {
            return if (lowered.contains("phone")) SMSAuthError.InvalidPhone else SMSAuthError.InvalidCode
        }

        when {
            code.contains("INVALID_PHONE", ignoreCase = true) ||
                lowered.contains("invalid format") ||
                lowered.contains("invalid phone") -> return SMSAuthError.InvalidPhone
            code.contains("INVALID_VERIFICATION", ignoreCase = true) ||
                code.contains("SESSION_EXPIRED", ignoreCase = true) ||
                lowered.contains("verification code") ||
                lowered.contains("invalid code") -> return SMSAuthError.InvalidCode
            code.contains("TOO_MANY", ignoreCase = true) ||
                code.contains("QUOTA", ignoreCase = true) ||
                lowered.contains("too many") ||
                lowered.contains("quota") ||
                lowered.contains("billing") -> return SMSAuthError.RateLimited
            code.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) -> {
                if (looksLikeRegionBlock(lowered)) return SMSAuthError.RegionNotAllowed
                return SMSAuthError.NotConfigured(
                    "Phone sign-in is disabled. Enable Authentication → Sign-in method → Phone.",
                )
            }
            code.contains("APP_NOT_AUTHORIZED", ignoreCase = true) ||
                code.contains("INVALID_APP_CREDENTIAL", ignoreCase = true) ||
                code.contains("CAPTCHA", ignoreCase = true) ||
                code.contains("MISSING_APP_CREDENTIAL", ignoreCase = true) ||
                code.contains("ERROR_APP_NOT_AUTHORIZED", ignoreCase = true) ||
                code.contains("MISSING_CLIENT_IDENTIFIER", ignoreCase = true) ||
                lowered.contains("app credential") ||
                lowered.contains("recaptcha") ||
                lowered.contains("play integrity") ||
                lowered.contains("safetynet") ||
                lowered.contains("app verification") ||
                lowered.contains("sha-1") ||
                lowered.contains("sha1") ||
                lowered.contains("this app is not authorized") ->
                return SMSAuthError.AppVerificationFailed
        }
        if (looksLikeRegionBlock(lowered)) return SMSAuthError.RegionNotAllowed
        val detail = buildString {
            if (code.isNotBlank()) append("[$code] ")
            append(message.ifBlank { "Firebase Auth error" })
        }
        return SMSAuthError.Underlying(detail)
    }

    private fun looksLikeRegionBlock(lowered: String): Boolean =
        lowered.contains("region") ||
            lowered.contains("blocked") ||
            lowered.contains("not authorized to send") ||
            lowered.contains("cannot be used") ||
            lowered.contains("sms cannot")

    companion object {
        private const val TAG = "FirebasePhoneSMS"
        private const val KEY_VERIFICATION_ID = "diary.firebase.authVerificationID"
        /** Wall-clock wait for callbacks (includes browser reCAPTCHA UI). */
        private const val SEND_TIMEOUT_MS = 120_000L
        private val pendingAutoCredential = AtomicReference<PhoneAuthCredential?>(null)
    }
}

/** Placeholder for Twilio Verify — fail closed. */
class TwilioReadySMSAuthProvider : SMSAuthProviding {
    override suspend fun sendCode(
        activity: Activity,
        phoneE164: String,
        resend: Boolean,
    ): SMSCodeSession {
        throw SMSAuthError.NotConfigured(
            "Twilio Verify client is not linked. Keep DIARY_SMS_PROVIDER empty/firebase.",
        )
    }

    override suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser {
        throw SMSAuthError.NotConfigured("Twilio Verify client is not linked.")
    }
}
