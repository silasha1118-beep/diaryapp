package com.diaryvault.app.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.diaryvault.app.BuildConfig
import com.diaryvault.app.config.FirebaseBootstrap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * Mainland +86 SMS OTP via Cloud Functions → Aliyun Dysmsapi (server-side keys),
 * then Firebase Auth **custom token** so Firestore still uses `users/{uid}`.
 *
 * No AccessKey in the APK. No Google reCAPTCHA on this path.
 *
 * When Functions are missing / Aliyun secrets unset:
 * - DEBUG: falls back to [stubFallback] (local OTP) so builds still run.
 * - Release: [SMSAuthError.NotConfigured].
 */
class AliyunSmsAuthProvider(
    private val appContext: Context,
    private val stubFallback: SMSAuthProviding = StubSMSAuthProvider(),
) : SMSAuthProviding {

    override suspend fun sendCode(
        activity: Activity,
        phoneE164: String,
        resend: Boolean,
    ): SMSCodeSession {
        val normalized = PhoneNumberNormalizer.normalizeToE164(phoneE164)
        if (!normalized.startsWith("+86") || !PhoneNumberNormalizer.isPlausibleE164(normalized)) {
            throw SMSAuthError.InvalidPhone
        }

        FirebaseBootstrap.configureIfNeeded(appContext)
        if (!FirebaseBootstrap.hasGoogleServicesJson || !FirebaseBootstrap.isConfigured) {
            return sendViaStubFallback(activity, normalized, resend, "Firebase not configured")
        }

        return try {
            val data = call("sendAliyunSmsOtp", mapOf("phoneE164" to normalized))
            val sessionId = data["sessionId"] as? String
            if (sessionId.isNullOrBlank()) {
                throw SMSAuthError.EmptyVerificationId
            }
            val stub = data["stub"] as? Boolean == true
            val hint = data["stubDebugHint"] as? String
            if (stub) {
                Log.w(TAG, "Aliyun CF returned stub mode (keys missing / ALLOW_STUB)")
            }
            SMSCodeSession(
                verificationID = sessionId,
                phoneE164 = normalized,
                stubDebugHint = if (BuildConfig.DEBUG) hint else null,
                autoVerified = false,
                path = if (stub) SmsAuthPath.STUB else SmsAuthPath.ALIYUN,
            )
        } catch (e: SMSAuthError) {
            throw e
        } catch (e: Exception) {
            val mapped = mapFunctionsError(e)
            if (shouldFallbackToStub(mapped)) {
                return sendViaStubFallback(activity, normalized, resend, mapped.message)
            }
            throw mapped
        }
    }

    override suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser {
        if (session.path == SmsAuthPath.STUB || session.verificationID.startsWith("stub-")) {
            return stubFallback.verify(code, session)
        }

        FirebaseBootstrap.configureIfNeeded(appContext)
        if (!FirebaseBootstrap.isConfigured) {
            throw SMSAuthError.NotConfigured(
                "Firebase is not configured. Add google-services.json to android/app/.",
            )
        }

        val trimmed = code.trim()
        if (trimmed.length < 4 || !trimmed.all { it.isDigit() }) throw SMSAuthError.InvalidCode

        return try {
            val data = call(
                "verifyAliyunSmsOtp",
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

    private suspend fun sendViaStubFallback(
        activity: Activity,
        phoneE164: String,
        resend: Boolean,
        reason: String?,
    ): SMSCodeSession {
        if (!BuildConfig.DEBUG) {
            throw SMSAuthError.NotConfigured(
                reason?.takeIf { it.isNotBlank() }
                    ?: "Aliyun SMS Cloud Functions are not available. Deploy functions and set ALIYUN_SMS_* secrets.",
            )
        }
        Log.w(TAG, "DEBUG: Aliyun path falling back to local stub — $reason")
        val session = stubFallback.sendCode(activity, phoneE164, resend)
        return session.copy(path = SmsAuthPath.STUB)
    }

    private fun shouldFallbackToStub(error: SMSAuthError): Boolean {
        if (!BuildConfig.DEBUG) return false
        return error is SMSAuthError.NotConfigured ||
            (error is SMSAuthError.Underlying &&
                error.detail.contains("NOT_FOUND", ignoreCase = true))
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
                    if (details.contains("code", ignoreCase = true)) SMSAuthError.InvalidCode
                    else SMSAuthError.InvalidPhone
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> SMSAuthError.RateLimited
                FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                    SMSAuthError.NotConfigured(details.ifBlank { "Aliyun SMS not configured" })
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
        return SMSAuthError.Underlying(msg.ifBlank { "Aliyun SMS error" })
    }

    companion object {
        private const val TAG = "AliyunSmsAuth"
        /** Must match `setGlobalOptions({ region })` in firebase/functions. */
        const val FUNCTIONS_REGION = "asia-east1"
    }
}
