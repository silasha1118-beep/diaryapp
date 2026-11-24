package com.diaryvault.app.auth

import android.app.Activity
import android.util.Log

/**
 * Dial-based SMS routing:
 * - **+86** (China mainland) → [chinaMainland] (Tencent Cloud SMS via Cloud Functions).
 *   When Tencent secrets / Functions are missing, [TencentSmsAuthProvider] falls back to
 *   Firebase Phone Auth so Console **test phone numbers** still work.
 *   DEBUG does **not** skip Tencent — company account must be testable from Studio.
 * - all other country codes → [other] (Firebase Phone Auth)
 *
 * Verify always follows [SMSCodeSession.path] from the send that produced the session —
 * never cross a China CF sessionId with a Firebase Phone Auth verificationId.
 */
class RoutingSMSAuthProvider(
    private val chinaMainland: SMSAuthProviding,
    private val other: SMSAuthProviding,
) : SMSAuthProviding {

    private fun pick(phoneE164: String): SMSAuthProviding {
        val normalized = PhoneNumberNormalizer.normalizeToE164(phoneE164)
        if (isChinaMainland(normalized)) {
            Log.i(VAULT_AUTH_TAG, "send provider=ChinaCF/Tencent (+86) e164=$normalized")
            return chinaMainland
        }
        Log.i(VAULT_AUTH_TAG, "send provider=FirebasePhone e164=$normalized")
        return other
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
            SmsAuthPath.TENCENT -> "ChinaCF/Tencent"
            SmsAuthPath.STUB -> "Stub"
        }
        Log.i(
            VAULT_AUTH_TAG,
            "verify provider=$providerLabel path=${session.path} " +
                "e164=${session.phoneE164} codeLen=${normalizedCode.length} " +
                "idLen=${session.verificationID.length} auto=${session.autoVerified}",
        )
        return when (session.path) {
            SmsAuthPath.FIREBASE -> other.verify(normalizedCode, session)
            SmsAuthPath.TENCENT, SmsAuthPath.STUB -> {
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
