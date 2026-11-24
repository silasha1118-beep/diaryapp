package com.diaryvault.app.config

import com.diaryvault.app.BuildConfig

/**
 * SMS / phone auth configuration. Override via BuildConfig / env — **never commit secrets**.
 *
 * - `DIARY_SMS_PROVIDER` — `stub` | `firebase` | `twilio`
 *   Empty / unset: **firebase** when `google-services.json` is present, else **stub**.
 * - When provider is **firebase**, dial routing still applies:
 *   **`+86` → Aliyun** (Cloud Functions; AccessKeys server-side only) when secrets are set;
 *   missing Aliyun keys / Functions → Firebase Phone Auth fallback (Console / DEBUG test codes);
 *   other dials → Firebase Phone Auth (Play Integrity preferred).
 * - Firebase uses local `android/app/google-services.json` (gitignored).
 * - Optional `DIARY_FIREBASE_PROJECT_ID` is a label only (default `diaryappsilas`).
 * - Aliyun keys must **not** be placed in BuildConfig / APK — see `firebase/ALIYUN_SMS.md`.
 */
object AuthConfig {
    enum class SMSProviderKind(val raw: String) {
        STUB("stub"),
        FIREBASE("firebase"),
        TWILIO("twilio"),
        ;

        companion object {
            fun fromRaw(raw: String?): SMSProviderKind =
                entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: STUB
        }
    }

    sealed class SMSProviderResolution {
        data class Explicit(val provider: SMSProviderKind) : SMSProviderResolution()
        data object AutoFirebaseJsonPresent : SMSProviderResolution()
        data object AutoStubNoJson : SMSProviderResolution()

        val kind: SMSProviderKind
            get() = when (this) {
                is Explicit -> provider
                AutoFirebaseJsonPresent -> SMSProviderKind.FIREBASE
                AutoStubNoJson -> SMSProviderKind.STUB
            }
    }

    val smsProviderResolution: SMSProviderResolution
        get() {
            val raw = BuildConfig.DIARY_SMS_PROVIDER.trim()
            if (raw.isNotEmpty()) {
                return SMSProviderResolution.Explicit(provider = SMSProviderKind.fromRaw(raw))
            }
            return if (FirebaseBootstrap.hasGoogleServicesJson) {
                SMSProviderResolution.AutoFirebaseJsonPresent
            } else {
                SMSProviderResolution.AutoStubNoJson
            }
        }

    val smsProvider: SMSProviderKind get() = smsProviderResolution.kind

    /** Optional project id label (e.g. `diaryappsilas`). Not required when json is present. */
    val firebaseProjectID: String?
        get() = BuildConfig.DIARY_FIREBASE_PROJECT_ID.trim().ifEmpty { null }

    /** Stub-only: when set, only this code verifies. When empty, stub accepts any 6-digit code. */
    val stubAcceptCode: String?
        get() = BuildConfig.DIARY_SMS_STUB_CODE.trim().ifEmpty { null }
}
