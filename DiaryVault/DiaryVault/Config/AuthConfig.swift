import Foundation

/// SMS / phone auth configuration. Override via scheme env or Info.plist — **never commit secrets**.
///
/// - `DIARY_SMS_PROVIDER` — `stub` (default) | `firebase` | `twilio`
/// - Firebase / Twilio keys are read only when that provider is selected; empty means “not wired yet”.
enum AuthConfig {
    enum SMSProviderKind: String {
        case stub
        case firebase
        case twilio
    }

    /// Active SMS backend. Defaults to local stub (no network, no secrets).
    static var smsProvider: SMSProviderKind {
        let raw = (value(for: "DIARY_SMS_PROVIDER") ?? "stub").lowercased()
        return SMSProviderKind(rawValue: raw) ?? .stub
    }

    // MARK: - Firebase-ready (placeholders — leave empty in git)

    /// Firebase / GoogleService project id (optional label only; SDK plist stays out of git).
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
            return firebaseProjectID != nil
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
