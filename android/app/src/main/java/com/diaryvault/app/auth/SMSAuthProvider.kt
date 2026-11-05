package com.diaryvault.app.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.diaryvault.app.config.AuthConfig
import com.diaryvault.app.config.FirebaseBootstrap
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.tasks.await

/** Result of requesting an SMS OTP. */
data class SMSCodeSession(
    val verificationID: String,
    val phoneE164: String,
    /** Stub-only hint for DEBUG UI; always null for Firebase. */
    val stubDebugHint: String? = null,
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
    data object RegionNotAllowed : SMSAuthError("SMS is not allowed for this phone region in Firebase Console")
    data object AppVerificationFailed : SMSAuthError(
        "App verification failed. Add the debug SHA-1 fingerprint in Firebase Console → Project settings → Your apps.",
    )
    data class Underlying(val detail: String) : SMSAuthError(detail)
}

interface SMSAuthProviding {
    suspend fun sendCode(activity: Activity, phoneE164: String): SMSCodeSession
    suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser
}

object SMSAuthProviderFactory {
    fun make(context: Context): SMSAuthProviding {
        FirebaseBootstrap.configureIfNeeded(context)
        return when (AuthConfig.smsProvider) {
            AuthConfig.SMSProviderKind.FIREBASE -> FirebasePhoneSMSAuthProvider(context.applicationContext)
            AuthConfig.SMSProviderKind.TWILIO -> TwilioReadySMSAuthProvider()
            AuthConfig.SMSProviderKind.STUB -> StubSMSAuthProvider()
        }
    }
}

/** Local OTP stub — does **not** send SMS. */
class StubSMSAuthProvider : SMSAuthProviding {
    private val issued = mutableMapOf<String, String>()

    override suspend fun sendCode(activity: Activity, phoneE164: String): SMSCodeSession {
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
 * Requires `android/app/google-services.json` (gitignored) and Phone provider enabled in Console.
 */
class FirebasePhoneSMSAuthProvider(
    private val appContext: Context,
) : SMSAuthProviding {
    private val forceResendToken = AtomicReference<ForceResendingToken?>(null)

    override suspend fun sendCode(activity: Activity, phoneE164: String): SMSCodeSession {
        val normalized = PhoneNumberNormalizer.normalizeToE164(phoneE164)
        if (!PhoneNumberNormalizer.isPlausibleE164(normalized)) throw SMSAuthError.InvalidPhone

        FirebaseBootstrap.configureIfNeeded(appContext)
        if (!FirebaseBootstrap.hasGoogleServicesJson || !FirebaseBootstrap.isConfigured) {
            throw SMSAuthError.NotConfigured(
                "google-services.json missing. Place it at android/app/google-services.json (gitignored), rebuild. Or set DIARY_SMS_PROVIDER=stub.",
            )
        }

        applySMSLanguageCode()

        return suspendCoroutine { cont ->
            val resumed = java.util.concurrent.atomic.AtomicBoolean(false)
            fun resumeOnce(block: () -> Unit) {
                if (resumed.compareAndSet(false, true)) block()
            }

            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Auto-retrieval / instant verification — stash credential; UI can verify with any code
                    // or we resume as an auto session for the gate to finish.
                    Log.d(TAG, "onVerificationCompleted (auto)")
                    pendingAutoCredential.set(credential)
                    resumeOnce {
                        cont.resume(
                            SMSCodeSession(
                                verificationID = "auto",
                                phoneE164 = normalized,
                                stubDebugHint = null,
                            ),
                        )
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    resumeOnce { cont.resumeWithException(mapFirebaseError(e)) }
                }

                override fun onCodeSent(verificationId: String, token: ForceResendingToken) {
                    forceResendToken.set(token)
                    prefs().edit().putString(KEY_VERIFICATION_ID, verificationId).apply()
                    resumeOnce {
                        cont.resume(
                            SMSCodeSession(
                                verificationID = verificationId,
                                phoneE164 = normalized,
                                stubDebugHint = null,
                            ),
                        )
                    }
                }
            }

            val builder = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                .setPhoneNumber(normalized)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
            forceResendToken.get()?.let { builder.setForceResendingToken(it) }
            PhoneAuthProvider.verifyPhoneNumber(builder.build())
        }
    }

    override suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser {
        val trimmed = code.trim()
        if (trimmed.length < 4 || !trimmed.all { it.isDigit() }) throw SMSAuthError.InvalidCode

        FirebaseBootstrap.configureIfNeeded(appContext)
        if (!FirebaseBootstrap.isConfigured) {
            throw SMSAuthError.NotConfigured(
                "Firebase is not configured. Add google-services.json to android/app/.",
            )
        }

        pendingAutoCredential.getAndSet(null)?.let { auto ->
            val result = FirebaseAuth.getInstance().signInWithCredential(auto).await()
            val user = result.user ?: throw SMSAuthError.Underlying("Firebase Auth returned no user")
            return SMSAuthUser(
                userID = user.uid,
                phoneE164 = user.phoneNumber ?: session.phoneE164,
            )
        }

        val verificationID = session.verificationID.ifEmpty {
            prefs().getString(KEY_VERIFICATION_ID, "").orEmpty()
        }
        if (verificationID.isEmpty() || verificationID.startsWith("auto")) {
            throw SMSAuthError.Underlying("Missing verification session. Request a new code.")
        }

        val credential = PhoneAuthProvider.getCredential(verificationID, trimmed)
        return try {
            val result = FirebaseAuth.getInstance().signInWithCredential(credential).await()
            prefs().edit().remove(KEY_VERIFICATION_ID).apply()
            val user = result.user ?: throw SMSAuthError.Underlying("Firebase Auth returned no user")
            SMSAuthUser(
                userID = user.uid,
                phoneE164 = user.phoneNumber ?: session.phoneE164,
            )
        } catch (e: Exception) {
            throw mapFirebaseError(e)
        }
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
        val code = (error as? FirebaseAuthException)?.errorCode.orEmpty()

        when {
            code.contains("INVALID_PHONE", ignoreCase = true) ||
                lowered.contains("invalid phone") -> return SMSAuthError.InvalidPhone
            code.contains("INVALID_VERIFICATION", ignoreCase = true) ||
                code.contains("SESSION_EXPIRED", ignoreCase = true) ||
                lowered.contains("verification code") ||
                lowered.contains("invalid code") -> return SMSAuthError.InvalidCode
            code.contains("TOO_MANY", ignoreCase = true) ||
                code.contains("QUOTA", ignoreCase = true) ||
                lowered.contains("too many") ||
                lowered.contains("quota") -> return SMSAuthError.RateLimited
            code.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) -> {
                if (looksLikeRegionBlock(lowered)) return SMSAuthError.RegionNotAllowed
                return SMSAuthError.NotConfigured(
                    "Phone sign-in is disabled in Firebase Console. Enable Authentication → Sign-in method → Phone.",
                )
            }
            code.contains("APP_NOT_AUTHORIZED", ignoreCase = true) ||
                code.contains("INVALID_APP_CREDENTIAL", ignoreCase = true) ||
                code.contains("CAPTCHA", ignoreCase = true) ||
                code.contains("MISSING_APP_CREDENTIAL", ignoreCase = true) ||
                lowered.contains("app credential") ||
                lowered.contains("recaptcha") ||
                lowered.contains("app verification") ||
                lowered.contains("sha") -> return SMSAuthError.AppVerificationFailed
        }
        if (looksLikeRegionBlock(lowered)) return SMSAuthError.RegionNotAllowed
        return SMSAuthError.Underlying(message.ifBlank { "Firebase Auth error" })
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
        private val pendingAutoCredential = AtomicReference<PhoneAuthCredential?>(null)
    }
}

/** Placeholder for Twilio Verify — fail closed. */
class TwilioReadySMSAuthProvider : SMSAuthProviding {
    override suspend fun sendCode(activity: Activity, phoneE164: String): SMSCodeSession {
        throw SMSAuthError.NotConfigured(
            "Twilio Verify client is not linked. Keep DIARY_SMS_PROVIDER empty/firebase or stub.",
        )
    }

    override suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser {
        throw SMSAuthError.NotConfigured("Twilio Verify client is not linked.")
    }
}
