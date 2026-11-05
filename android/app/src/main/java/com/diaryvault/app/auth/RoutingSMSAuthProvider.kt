package com.diaryvault.app.auth

import android.app.Activity

/**
 * Dial-based SMS routing:
 * - **+86** (China mainland) → [chinaMainland] (Aliyun via Cloud Functions)
 * - all other country codes → [other] (Firebase Phone Auth)
 */
class RoutingSMSAuthProvider(
    private val chinaMainland: SMSAuthProviding,
    private val other: SMSAuthProviding,
) : SMSAuthProviding {

    private fun pick(phoneE164: String): SMSAuthProviding {
        val normalized = PhoneNumberNormalizer.normalizeToE164(phoneE164)
        return if (isChinaMainland(normalized)) chinaMainland else other
    }

    override suspend fun sendCode(
        activity: Activity,
        phoneE164: String,
        resend: Boolean,
    ): SMSCodeSession = pick(phoneE164).sendCode(activity, phoneE164, resend)

    override suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser {
        return when (session.path) {
            SmsAuthPath.ALIYUN, SmsAuthPath.STUB -> {
                // Stub sessions from Aliyun DEBUG fallback still verify on chinaMainland
                // (which delegates stub verification IDs).
                if (session.phoneE164.let { isChinaMainland(it) }) {
                    chinaMainland.verify(code, session)
                } else {
                    other.verify(code, session)
                }
            }
            SmsAuthPath.FIREBASE -> other.verify(code, session)
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
