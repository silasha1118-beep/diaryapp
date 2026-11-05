package com.diaryvault.app.auth

import android.app.Activity
import com.diaryvault.app.BuildConfig

/**
 * Dial-based SMS routing:
 * - **+86** (China mainland) → [chinaMainland] (Aliyun via Cloud Functions) in **release**
 *   when Aliyun is configured
 * - **DEBUG** (or when Aliyun keys are missing — see [AliyunSmsAuthProvider]) → Firebase Phone Auth
 *   so Firebase Console **test phone numbers** work for +86
 * - all other country codes → [other] (Firebase Phone Auth)
 */
class RoutingSMSAuthProvider(
    private val chinaMainland: SMSAuthProviding,
    private val other: SMSAuthProviding,
    /**
     * When true, never call Aliyun for +86 — use Firebase Phone Auth (Console test numbers).
     * Default: DEBUG builds. Release keeps Aliyun unless the Aliyun provider itself falls back.
     */
    private val preferFirebaseForChinaMainland: Boolean = BuildConfig.DEBUG,
) : SMSAuthProviding {

    private fun pick(phoneE164: String): SMSAuthProviding {
        val normalized = PhoneNumberNormalizer.normalizeToE164(phoneE164)
        if (!isChinaMainland(normalized)) return other
        if (preferFirebaseForChinaMainland) return other
        return chinaMainland
    }

    override suspend fun sendCode(
        activity: Activity,
        phoneE164: String,
        resend: Boolean,
    ): SMSCodeSession = pick(phoneE164).sendCode(activity, phoneE164, resend)

    override suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser {
        // Always verify on the backend that produced the session — never cross Aliyun
        // sessionId with Firebase verificationId (or vice versa).
        return when (session.path) {
            SmsAuthPath.FIREBASE -> other.verify(code, session)
            SmsAuthPath.ALIYUN, SmsAuthPath.STUB -> {
                if (session.phoneE164.let { isChinaMainland(it) }) {
                    chinaMainland.verify(code, session)
                } else {
                    other.verify(code, session)
                }
            }
        }
    }

    override fun clearSendState() {
        chinaMainland.clearSendState()
        other.clearSendState()
    }

    companion object {
        fun isChinaMainland(phoneE164: String): Boolean {
            val n = PhoneNumberNormalizer.normalizeToE164(phoneE164)
            return n.startsWith("+86") && !n.startsWith("+860") // defensive; +86 only
        }
    }
}
