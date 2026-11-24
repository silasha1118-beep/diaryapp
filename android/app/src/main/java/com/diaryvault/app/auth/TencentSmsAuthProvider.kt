package com.diaryvault.app.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.diaryvault.app.config.FirebaseBootstrap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * Mainland +86 SMS OTP via Cloud Functions → Tencent Cloud SMS (server-side keys),
 * then Firebase Auth **custom token** so Firestore still uses `users/{uid}`.
 *
 * No SecretId/SecretKey in the APK. No Google reCAPTCHA on this path.
 *
 * DEBUG and release both hit Tencent first so company accounts can be tested from
 * Android Studio. When Functions are missing / secrets unset / CF returns stub mode:
 * fall back to [firebaseFallback] (Firebase Phone Auth) so Firebase Console
 * **test phone numbers** (e.g. 123456) work — never a local stub OTP that
 * ignores Console test codes.
 */
class TencentSmsAuthProvider(
    private val appContext: Context,
    private val firebaseFallback: SMSAuthProviding,
) : SMSAuthProviding {

    override suspend fun sendCode(
        activity: Activity,
        phoneE164: String,
        resend: Boolean,
    ): SMSCodeSession {
        val normalized = PhoneNumberNormalizer.normalizeToE164(phoneE164)
        // Use isChinaMainland — raw startsWith("+86") would also match Taiwan +886.
        if (!RoutingSMSAuthProvider.isChinaMainland(normalized) ||
            !PhoneNumberNormalizer.isPlausibleE164(normalized)
        ) {
            throw SMSAuthError.InvalidPhone
        }

        FirebaseBootstrap.configureIfNeeded(appContext)
        if (!FirebaseBootstrap.hasGoogleServicesJson || !FirebaseBootstrap.isConfigured) {
            return sendViaFirebaseFallback(
                activity,
                normalized,
                resend,
                "Firebase not configured for Tencent path",
            )
        }

        return try {
            val data = call("sendTencentSmsOtp", mapOf("phoneE164" to normalized))
            val sessionId = data["sessionId"] as? String
            if (sessionId.isNullOrBlank()) {
                throw SMSAuthError.EmptyVerificationId
            }
            val stub = data["stub"] as? Boolean == true
            if (stub) {
                // Keys missing / ALLOW_STUB — do not accept server stub OTP.
                // Firebase Console test numbers only work on Firebase Phone Auth.
                Log.w(
                    TAG,
                    "Tencent CF returned stub mode (keys missing / ALLOW_STUB) — " +
                        "falling back to Firebase Phone Auth for Console test numbers",
                )
                return sendViaFirebaseFallback(
                    activity,
                    normalized,
                    resend,
                    "Tencent keys missing (CF stub mode)",
                )
            }
            Log.i(
                RoutingSMSAuthProvider.VAULT_AUTH_TAG,
                "send provider=ChinaCF/Tencent e164=$normalized sessionLen=${sessionId.length}",
            )
            SMSCodeSession(
                verificationID = sessionId,
                phoneE164 = normalized,
                stubDebugHint = null,
                autoVerified = false,
                path = SmsAuthPath.TENCENT,
            )
        } catch (e: SMSAuthError) {
            throw e
        } catch (e: Exception) {
            val mapped = mapFunctionsError(e)
            if (shouldFallbackToFirebase(mapped)) {
                return sendViaFirebaseFallback(activity, normalized, resend, mapped.message)
            }
            throw mapped
        }
    }

    override suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser {
        val trimmed = PhoneNumberNormalizer.normalizeOtpDigits(code)
        // Stub / Firebase sessions must not hit verifyTencentSmsOtp with a Phone Auth id.
        if (session.path == SmsAuthPath.FIREBASE) {
            Log.i(
                RoutingSMSAuthProvider.VAULT_AUTH_TAG,
                "verify provider=FirebasePhone (via Tencent wrapper) e164=${session.phoneE164}",
            )
            return firebaseFallback.verify(trimmed, session)
        }
        if (session.path == SmsAuthPath.STUB || session.verificationID.startsWith("stub-")) {
            Log.w(TAG, "Unexpected stub session on Tencent path — trying Firebase verify")
            return firebaseFallback.verify(trimmed, session)
        }
        Log.i(
            RoutingSMSAuthProvider.VAULT_AUTH_TAG,
            "verify provider=ChinaCF/Tencent e164=${session.phoneE164} codeLen=${trimmed.length}",
        )

        FirebaseBootstrap.configureIfNeeded(appContext)
        if (!FirebaseBootstrap.isConfigured) {
            throw SMSAuthError.NotConfigured(
                "Firebase is not configured. Add google-services.json to android/app/.",
            )
        }

        if (trimmed.length < 4 || !trimmed.all { it.isDigit() }) throw SMSAuthError.InvalidCode()

        return try {
            val data = call(
                "verifyTencentSmsOtp",
                mapOf(
                    "sessionId" to session.verificationID,
                    "code" to trimmed,
                ),
            )
            val customToken = data["customToken"] as? String
            if (customToken.isNullOrBlank()) {
                throw SMSAuthError.Underlying("Server did not return a custom token")
            }
            val result = FirebaseAuth.getInstance().signInWithCustomToken(customToken).await()
            val user = result.user ?: throw SMSAuthError.Underlying("Custom token sign-in returned no user")
            SMSAuthUser(
                userID = user.uid,
                phoneE164 = user.phoneNumber ?: session.phoneE164,
            )
        } catch (e: SMSAuthError) {
            throw e
        } catch (e: Exception) {
            throw mapFunctionsError(e)
        }
    }

    private suspend fun sendViaFirebaseFallback(
        activity: Activity,
        phoneE164: String,
        resend: Boolean,
        reason: String?,
    ): SMSCodeSession {
        Log.w(TAG, "Tencent path → Firebase Phone Auth fallback — $reason")
        return firebaseFallback.sendCode(activity, phoneE164, resend)
    }

    private fun shouldFallbackToFirebase(error: SMSAuthError): Boolean {
        // Missing Tencent / Functions → Firebase (Console test numbers).
        if (error is SMSAuthError.NotConfigured) return true
        return error is SMSAuthError.Underlying &&
            error.detail.contains("NOT_FOUND", ignoreCase = true)
    }

    private suspend fun call(name: String, payload: Map<String, Any>): Map<*, *> {
        val functions = FirebaseFunctions.getInstance(FUNCTIONS_REGION)
        val result = functions
            .getHttpsCallable(name)
            .call(payload)
            .await()
        @Suppress("UNCHECKED_CAST")
        return (result.getData() as? Map<*, *>)
            ?: throw SMSAuthError.Underlying("Empty response from $name")
    }

    private fun mapFunctionsError(error: Exception): SMSAuthError {
        if (error is SMSAuthError) return error
        if (error is FirebaseFunctionsException) {
            val details = error.message.orEmpty()
            return when (error.code) {
                FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                    if (details.contains("code", ignoreCase = true)) SMSAuthError.InvalidCode()
                    else SMSAuthError.InvalidPhone
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> SMSAuthError.RateLimited
                FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                    SMSAuthError.NotConfigured(details.ifBlank { "Tencent SMS not configured" })
                FirebaseFunctionsException.Code.NOT_FOUND ->
                    SMSAuthError.NotConfigured(
                        "Cloud Function not deployed. From firebase/: firebase deploy --only functions",
                    )
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                -> SMSAuthError.TimedOut
                else -> SMSAuthError.Underlying(
                    buildString {
                        append("[${error.code}] ")
                        append(details.ifBlank { "Cloud Function error" })
                    },
                )
            }
        }
        val msg = error.message.orEmpty()
        if (msg.contains("NOT_FOUND", ignoreCase = true) ||
            msg.contains("NOT FOUND", ignoreCase = true)
        ) {
            return SMSAuthError.NotConfigured(
                "Cloud Function not deployed. From firebase/: firebase deploy --only functions",
            )
        }
        return SMSAuthError.Underlying(msg.ifBlank { "Tencent SMS error" })
    }

    companion object {
        private const val TAG = "TencentSmsAuth"
        /** Must match `setGlobalOptions({ region })` in firebase/functions. */
        const val FUNCTIONS_REGION = "asia-east1"
    }
}
