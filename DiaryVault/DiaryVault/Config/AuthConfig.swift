import Foundation

/// SMS / phone auth configuration. Override via scheme env or Info.plist — **never commit secrets**.
///
/// - `DIARY_SMS_PROVIDER` — `stub` | `firebase` | `twilio`
///   Empty / unset: **firebase** when `GoogleService-Info.plist` is in the bundle, else **stub**.
/// - Firebase uses the local plist (gitignored). Optional `DIARY_FIREBASE_PROJECT_ID` is a label only.
/// - Twilio keys are read only when that provider is selected.
enum AuthConfig {
    enum SMSProviderKind: String {
        case stub
        case firebase
        case twilio
    }

    /// Active SMS backend.
    static var smsProvider: SMSProviderKind {
        if let raw = value(for: "DIARY_SMS_PROVIDER")?.lowercased(), !raw.isEmpty {
            return SMSProviderKind(rawValue: raw) ?? .stub
        }
        // Auto-select Firebase once the (gitignored) plist is in the app target.
        if FirebaseBootstrap.hasGoogleServiceInfoPlist {
            return .firebase
        }
        return .stub
    }

    // MARK: - Firebase (plist is source of truth — leave secrets out of git)

    /// Optional project id label (e.g. `diaryappsilas`). Not required when plist is present.
    static var firebaseProjectID: String? { value(for: "DIARY_FIREBASE_PROJECT_ID") }

    // MARK: - Twilio-ready (placeholders — leave empty in git)

    static var twilioAccountSID: String? { value(for: "DIARY_TWILIO_ACCOUNT_SID") }
    static var twilioAuthToken: String? { value(for: "DIARY_TWILIO_AUTH_TOKEN") }
    static var twilioVerifyServiceSID: String? { value(for: "DIARY_TWILIO_VERIFY_SERVICE_SID") }

    /// Stub-only: when set, only this code verifies. When empty, stub accepts any 6-digit code.
    static var stubAcceptCode: String? { value(for: "DIARY_SMS_STUB_CODE") }

    static var isRemoteSMSConfigured: Bool {
        switch smsProvider {
        case .stub:
            return true
        case .firebase:
            return FirebaseBootstrap.hasGoogleServiceInfoPlist || firebaseProjectID != nil
        case .twilio:
            return twilioAccountSID != nil
                && twilioAuthToken != nil
                && twilioVerifyServiceSID != nil
        }
    }

    private static func value(for key: String) -> String? {
        if let env = ProcessInfo.processInfo.environment[key], !env.isEmpty {
            return env
        }
        if let plist = Bundle.main.object(forInfoDictionaryKey: key) as? String, !plist.isEmpty {
            return plist
        }
        return nil
    }
}
