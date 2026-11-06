package com.diaryvault.app.auth

import android.app.Activity
import android.util.Log
import com.diaryvault.app.BuildConfig

/**
 * Dial-based SMS routing:
 * - **+86** (China mainland) → [chinaMainland] (Aliyun / future Tencent via Cloud Functions)
 *   in **release** when that gateway is configured
 * - **DEBUG** (and when the China gateway is not fully configured — see the China provider)
 *   → Firebase Phone Auth so Firebase Console **test phone numbers** work for +86
 * - all other country codes → [other] (Firebase Phone Auth)
 *
 * Verify always follows [SMSCodeSession.path] from the send that produced the session —
 * never cross a China CF sessionId with a Firebase Phone Auth verificationId.
 */
class RoutingSMSAuthProvider(
    private val chinaMainland: SMSAuthProviding,
    private val other: SMSAuthProviding,
    /**
     * When true, never call the China CF gateway for +86 — use Firebase Phone Auth
     * (Console test numbers). Default: DEBUG builds. Release keeps the China gateway
     * unless that provider itself falls back to Firebase.
     *
     * Always OR'd with [BuildConfig.DEBUG] inside [pick] so a future Tencent-style
     * constructor that omits this flag cannot regress inland Console test codes.
     */
    private val preferFirebaseForChinaMainland: Boolean = BuildConfig.DEBUG,
) : SMSAuthProviding {

    private fun pick(phoneE164: String): SMSAuthProviding {
        val normalized = PhoneNumberNormalizer.normalizeToE164(phoneE164)
        if (!isChinaMainland(normalized)) return other
        // Belt-and-suspenders: DEBUG always Firebase, even if a caller passed false.
        val forceFirebase = preferFirebaseForChinaMainland || BuildConfig.DEBUG
        if (forceFirebase) {
            Log.i(
                VAULT_AUTH_TAG,
                "send provider=FirebasePhone (+86 DEBUG force) " +
                    "DEBUG=${BuildConfig.DEBUG} preferFirebase=$preferFirebaseForChinaMainland " +
                    "e164=$normalized",
            )
            return other
        }
        Log.i(VAULT_AUTH_TAG, "send provider=ChinaCF (release +86) e164=$normalized")
        return chinaMainland
    }

    override suspend fun sendCode(
        activity: Activity,
        phoneE164: String,
        resend: Boolean,
    ): SMSCodeSession = pick(phoneE164).sendCode(activity, phoneE164, resend)

    override suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser {
        // Always verify on the backend that produced the session — never cross China CF
        // sessionId with Firebase Phone Auth verificationId (or vice versa).
        val normalizedCode = PhoneNumberNormalizer.normalizeOtpDigits(code)
        val providerLabel = when (session.path) {
            SmsAuthPath.FIREBASE -> "FirebasePhone"
            SmsAuthPath.ALIYUN -> "ChinaCF/Aliyun"
            SmsAuthPath.STUB -> "Stub"
        }
        Log.i(
            VAULT_AUTH_TAG,
            "verify provider=$providerLabel path=${session.path} " +
                "e164=${session.phoneE164} codeLen=${normalizedCode.length} " +
                "idLen=${session.verificationID.length} auto=${session.autoVerified} " +
                "DEBUG=${BuildConfig.DEBUG}",
        )
        if (BuildConfig.DEBUG && session.path != SmsAuthPath.FIREBASE) {
            Log.e(
                VAULT_AUTH_TAG,
                "DEBUG OTP expected Firebase path, got ${session.path} — " +
                    "wrong provider would reject Console test codes. Reinstall Debug APK.",
            )
            throw SMSAuthError.Underlying(
                "DEBUG build expected Firebase OTP, got ${session.path}. " +
                    "Uninstall the app (or clear data), then install the Debug build again.",
            )
        }
        return when (session.path) {
            SmsAuthPath.FIREBASE -> other.verify(normalizedCode, session)
            SmsAuthPath.ALIYUN, SmsAuthPath.STUB -> {
                if (session.phoneE164.let { isChinaMainland(it) }) {
                    chinaMainland.verify(normalizedCode, session)
                } else {
                    other.verify(normalizedCode, session)
                }
            }
        }
    }

    override fun clearSendState() {
        chinaMainland.clearSendState()
        other.clearSendState()
    }

    companion object {
        /** Same tag as [com.diaryvault.app.VaultViewModel] send/verify logs. */
        const val VAULT_AUTH_TAG = "VaultAuth"

        /**
         * Mainland China mobiles only. Must **not** treat `+886` (Taiwan) as `+86`.
         * `+852` / `+853` do not start with `+86`.
         */
        fun isChinaMainland(phoneE164: String): Boolean {
            val n = PhoneNumberNormalizer.normalizeToE164(phoneE164)
            return n.startsWith("+86") && !n.startsWith("+886")
        }
    }
}
