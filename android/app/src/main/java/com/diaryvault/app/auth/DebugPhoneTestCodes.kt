package com.diaryvault.app.auth

import com.diaryvault.app.BuildConfig

/**
 * DEBUG-only allowlist of Firebase Console **Phone numbers for testing**.
 *
 * Used with [com.google.firebase.auth.FirebaseAuthSettings.setAutoRetrievedSmsCodeForPhoneNumber]
 * so Debug builds complete Phone Auth via the official Firebase test API
 * (`onVerificationCompleted`) instead of a fragile typed `getCredential` path on MIUI.
 *
 * Release builds always see an empty map — never ship hardcoded test codes.
 */
object DebugPhoneTestCodes {
    /**
     * Parse `+E164:code,+E164:code` from [BuildConfig.DIARY_DEBUG_PHONE_TEST_CODES].
     * Empty / blank entries are skipped. Only consulted when [BuildConfig.DEBUG] is true.
     */
    fun pairs(): Map<String, String> {
        if (!BuildConfig.DEBUG) return emptyMap()
        val raw = BuildConfig.DIARY_DEBUG_PHONE_TEST_CODES.trim()
        if (raw.isEmpty()) return emptyMap()
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                val parts = entry.split(':', limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val e164 = PhoneNumberNormalizer.normalizeToE164(parts[0])
                val code = PhoneNumberNormalizer.normalizeOtpDigits(parts[1])
                if (!PhoneNumberNormalizer.isPlausibleE164(e164)) return@mapNotNull null
                if (code.length !in 4..8 || code.any { it !in '0'..'9' }) return@mapNotNull null
                e164 to code
            }
            .toMap()
    }

    fun codeFor(phoneE164: String): String? {
        val e164 = PhoneNumberNormalizer.normalizeToE164(phoneE164)
        return pairs()[e164]
    }
}
