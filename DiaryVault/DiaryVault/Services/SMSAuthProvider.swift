import Foundation

/// Result of requesting an SMS OTP. `verificationID` is opaque (Firebase session / Twilio SID / stub id).
struct SMSCodeSession: Equatable, Sendable {
    let verificationID: String
    let phoneE164: String
    /// Stub-only hint for DEBUG UI; always nil for real providers.
    let stubDebugHint: String?
}

enum SMSAuthError: LocalizedError, Equatable {
    case invalidPhone
    case invalidCode
    case notConfigured(String)
    case rateLimited
    case underlying(String)

    var errorDescription: String? {
        switch self {
        case .invalidPhone: return "Invalid phone number"
        case .invalidCode: return "Invalid verification code"
        case .notConfigured(let detail): return detail
        case .rateLimited: return "Too many attempts; try again later"
        case .underlying(let message): return message
        }
    }
}

/// Pluggable SMS OTP backend. Stub by default; Firebase Auth Phone / Twilio Verify can implement the same API.
protocol SMSAuthProviding: Sendable {
    func sendCode(toPhoneE164 phone: String) async throws -> SMSCodeSession
    func verify(code: String, session: SMSCodeSession) async throws
}

enum SMSAuthProviderFactory {
    @MainActor
    static func make() -> any SMSAuthProviding {
        switch AuthConfig.smsProvider {
        case .stub:
            return StubSMSAuthProvider()
        case .firebase:
            return FirebaseReadySMSAuthProvider()
        case .twilio:
            return TwilioReadySMSAuthProvider()
        }
    }
}

// MARK: - Stub (default · no secrets · no real SMS)

/// Local OTP stub for simulator / CI. Does **not** send SMS.
///
/// Verification rules:
/// - If `DIARY_SMS_STUB_CODE` is set, only that code succeeds.
/// - Otherwise any **6-digit** numeric code succeeds (documented in README).
final class StubSMSAuthProvider: SMSAuthProviding, @unchecked Sendable {
    private let lock = NSLock()
    private var issued: [String: String] = [:]

    func sendCode(toPhoneE164 phone: String) async throws -> SMSCodeSession {
        let normalized = PhoneNumberNormalizer.normalizeToE164(phone)
        guard PhoneNumberNormalizer.isPlausibleE164(normalized) else {
            throw SMSAuthError.invalidPhone
        }
        try await Task.sleep(nanoseconds: 350_000_000)
        let verificationID = "stub-" + UUID().uuidString
        let expected = AuthConfig.stubAcceptCode ?? Self.generateDebugCode()
        lock.lock()
        issued[verificationID] = expected
        lock.unlock()
        let hint: String? = {
            #if DEBUG
            return expected
            #else
            return nil
            #endif
        }()
        return SMSCodeSession(verificationID: verificationID, phoneE164: normalized, stubDebugHint: hint)
    }

    func verify(code: String, session: SMSCodeSession) async throws {
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count == 6, trimmed.allSatisfy(\.isNumber) else {
            throw SMSAuthError.invalidCode
        }
        try await Task.sleep(nanoseconds: 250_000_000)

        if let fixed = AuthConfig.stubAcceptCode {
            guard trimmed == fixed else { throw SMSAuthError.invalidCode }
            return
        }

        lock.lock()
        let expected = issued[session.verificationID]
        lock.unlock()
        // Accept the issued stub code **or** any 6-digit when session unknown (fresh install edge).
        if let expected, trimmed != expected {
            throw SMSAuthError.invalidCode
        }
    }

    private static func generateDebugCode() -> String {
        String(format: "%06d", Int.random(in: 0...999_999))
    }
}

// MARK: - Firebase-ready shell (no SDK · no secrets in repo)

/// Placeholder for Firebase Auth phone verification.
/// Selecting `DIARY_SMS_PROVIDER=firebase` without project config fails closed.
final class FirebaseReadySMSAuthProvider: SMSAuthProviding, @unchecked Sendable {
    func sendCode(toPhoneE164 phone: String) async throws -> SMSCodeSession {
        let normalized = PhoneNumberNormalizer.normalizeToE164(phone)
        guard PhoneNumberNormalizer.isPlausibleE164(normalized) else {
            throw SMSAuthError.invalidPhone
        }
        guard AuthConfig.firebaseProjectID != nil else {
            throw SMSAuthError.notConfigured(
                "Firebase SMS not configured. Set DIARY_FIREBASE_PROJECT_ID and add GoogleService-Info.plist locally (gitignored). Or use DIARY_SMS_PROVIDER=stub."
            )
        }
        throw SMSAuthError.notConfigured(
            "Firebase Auth Phone SDK is not linked in this MVP. Keep DIARY_SMS_PROVIDER=stub until the SDK is added."
        )
    }

    func verify(code: String, session: SMSCodeSession) async throws {
        throw SMSAuthError.notConfigured(
            "Firebase Auth Phone SDK is not linked in this MVP."
        )
    }
}

// MARK: - Twilio-ready shell (no SDK · no secrets in repo)

/// Placeholder for Twilio Verify.
/// Selecting `DIARY_SMS_PROVIDER=twilio` without SIDs/token fails closed.
final class TwilioReadySMSAuthProvider: SMSAuthProviding, @unchecked Sendable {
    func sendCode(toPhoneE164 phone: String) async throws -> SMSCodeSession {
        let normalized = PhoneNumberNormalizer.normalizeToE164(phone)
        guard PhoneNumberNormalizer.isPlausibleE164(normalized) else {
            throw SMSAuthError.invalidPhone
        }
        guard AuthConfig.isRemoteSMSConfigured else {
            throw SMSAuthError.notConfigured(
                "Twilio Verify not configured. Set DIARY_TWILIO_ACCOUNT_SID, DIARY_TWILIO_AUTH_TOKEN, DIARY_TWILIO_VERIFY_SERVICE_SID in scheme env (never commit). Or use DIARY_SMS_PROVIDER=stub."
            )
        }
        throw SMSAuthError.notConfigured(
            "Twilio Verify client is not linked in this MVP. Keep DIARY_SMS_PROVIDER=stub until the backend/SDK is added."
        )
    }

    func verify(code: String, session: SMSCodeSession) async throws {
        throw SMSAuthError.notConfigured(
            "Twilio Verify client is not linked in this MVP."
        )
    }
}

// MARK: - Phone helpers

enum PhoneNumberNormalizer {
    /// Launch-region dial codes shown in the gate UI.
    enum RegionDial: String, CaseIterable, Identifiable {
        case cn = "+86"
        case hk = "+852"
        case tw = "+886"

        var id: String { rawValue }

        var labelKey: String {
            switch self {
            case .cn: return "auth.dial.cn"
            case .hk: return "auth.dial.hk"
            case .tw: return "auth.dial.tw"
            }
        }
    }

    static func normalizeToE164(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("+") {
            let digits = trimmed.dropFirst().filter(\.isNumber)
            return "+" + digits
        }
        return "+" + trimmed.filter(\.isNumber)
    }

    static func composeE164(dial: RegionDial, nationalNumber: String) -> String {
        let digits = nationalNumber.filter(\.isNumber)
        // Drop a single leading 0 common in national formats.
        let national = digits.hasPrefix("0") ? String(digits.dropFirst()) : digits
        return dial.rawValue + national
    }

    static func isPlausibleE164(_ e164: String) -> Bool {
        guard e164.hasPrefix("+") else { return false }
        let digits = e164.dropFirst().filter(\.isNumber)
        // E.164 max 15 digits; require at least country + short local.
        return digits.count >= 8 && digits.count <= 15
    }

    static func maskedDisplay(_ e164: String) -> String {
        let digits = Array(e164)
        guard digits.count > 6 else { return e164 }
        let prefix = String(digits.prefix(4))
        let suffix = String(digits.suffix(2))
        return prefix + String(repeating: "•", count: max(0, digits.count - 6)) + suffix
    }
}
