package com.diaryvault.app.auth

import android.app.Activity

/**
 * Dial-based SMS routing:
 * - **+86** (China mainland) → [chinaMainland] (Tencent Cloud SMS via Cloud Functions)
 *   When Tencent secrets / Functions are missing, [TencentSmsAuthProvider] falls back to
 *   Firebase Phone Auth so Console **test phone numbers** still work.
 *   DEBUG does **not** skip Tencent — company account must be testable from Studio.
 * - all other country codes → [other] (Firebase Phone Auth)
 */
class RoutingSMSAuthProvider(
    private val chinaMainland: SMSAuthProviding,
    private val other: SMSAuthProviding,
) : SMSAuthProviding {

    private fun pick(phoneE164: String): SMSAuthProviding {
        val normalized = PhoneNumberNormalizer.normalizeToE164(phoneE164)
        if (isChinaMainland(normalized)) return chinaMainland
        return other
    }

    override suspend fun sendCode(
        activity: Activity,
        phoneE164: String,
        resend: Boolean,
    ): SMSCodeSession = pick(phoneE164).sendCode(activity, phoneE164, resend)

    override suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser {
        // Always verify on the backend that produced the session — never cross Tencent
        // sessionId with Firebase verificationId (or vice versa).
        return when (session.path) {
            SmsAuthPath.FIREBASE -> other.verify(code, session)
            SmsAuthPath.TENCENT, SmsAuthPath.STUB -> {
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
