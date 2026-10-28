import Foundation
import FirebaseCore
import FirebaseAuth

/// Result of requesting an SMS OTP. `verificationID` is opaque (Firebase session / Twilio SID / stub id).
struct SMSCodeSession: Equatable, Sendable {
    let verificationID: String
    let phoneE164: String
    /// Stub-only hint for DEBUG UI; always nil for real providers.
    let stubDebugHint: String?
}

/// Signed-in identity after a successful OTP verify.
struct SMSAuthUser: Equatable, Sendable {
    /// Stable user id (Firebase UID when using Firebase; synthetic for stub).
    let userID: String
    let phoneE164: String
}

enum SMSAuthError: LocalizedError, Equatable {
    case invalidPhone
    case invalidCode
    case notConfigured(String)
    case rateLimited
    /// Firebase SMS region policy blocked this country (allow TW / HK / CN in Console).
    case regionNotAllowed
    /// reCAPTCHA / APNs app verification failed — usually missing URL scheme (`REVERSED_CLIENT_ID`).
    case appVerificationFailed
    case underlying(String)

    var errorDescription: String? {
        switch self {
        case .invalidPhone: return "Invalid phone number"
        case .invalidCode: return "Invalid verification code"
        case .notConfigured(let detail): return detail
        case .rateLimited: return "Too many attempts; try again later"
        case .regionNotAllowed:
            return "SMS is not allowed for this phone region in Firebase Console"
        case .appVerificationFailed:
            return "App verification failed. Add REVERSED_CLIENT_ID as a URL Type in Xcode"
        case .underlying(let message): return message
        }
    }
}

/// Pluggable SMS OTP backend. Stub by default; Firebase Auth Phone is wired; Twilio remains a shell.
protocol SMSAuthProviding: Sendable {
    func sendCode(toPhoneE164 phone: String) async throws -> SMSCodeSession
    func verify(code: String, session: SMSCodeSession) async throws -> SMSAuthUser
}

enum SMSAuthProviderFactory {
    @MainActor
    static func make() -> any SMSAuthProviding {
        switch AuthConfig.smsProvider {
        case .stub:
            return StubSMSAuthProvider()
        case .firebase:
            return FirebasePhoneSMSAuthProvider()
        case .twilio:
            return TwilioReadySMSAuthProvider()
        }
    }
}

// MARK: - Stub (default when no GoogleService-Info.plist · no secrets · no real SMS)

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

    func verify(code: String, session: SMSCodeSession) async throws -> SMSAuthUser {
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count == 6, trimmed.allSatisfy(\.isNumber) else {
            throw SMSAuthError.invalidCode
        }
        try await Task.sleep(nanoseconds: 250_000_000)

        if let fixed = AuthConfig.stubAcceptCode {
            guard trimmed == fixed else { throw SMSAuthError.invalidCode }
            return SMSAuthUser(
                userID: "phone-" + session.phoneE164.filter(\.isNumber),
                phoneE164: session.phoneE164
            )
        }

        lock.lock()
        let expected = issued[session.verificationID]
        lock.unlock()
        // Accept the issued stub code **or** any 6-digit when session unknown (fresh install edge).
        if let expected, trimmed != expected {
            throw SMSAuthError.invalidCode
        }
        return SMSAuthUser(
            userID: "phone-" + session.phoneE164.filter(\.isNumber),
            phoneE164: session.phoneE164
        )
    }

    private static func generateDebugCode() -> String {
        String(format: "%06d", Int.random(in: 0...999_999))
    }
}

// MARK: - Firebase Auth Phone

/// Real Firebase Phone Auth: send SMS OTP → verify code → `Auth.auth().signIn`.
/// Requires `GoogleService-Info.plist` in the app target (gitignored) and Phone provider enabled in Console.
final class FirebasePhoneSMSAuthProvider: SMSAuthProviding, @unchecked Sendable {
    func sendCode(toPhoneE164 phone: String) async throws -> SMSCodeSession {
        let normalized = PhoneNumberNormalizer.normalizeToE164(phone)
        guard PhoneNumberNormalizer.isPlausibleE164(normalized) else {
            throw SMSAuthError.invalidPhone
        }

        FirebaseBootstrap.configureIfNeeded()
        guard FirebaseBootstrap.hasGoogleServiceInfoPlist, FirebaseApp.app() != nil else {
            throw SMSAuthError.notConfigured(
                "GoogleService-Info.plist missing from the app bundle. Add it in Xcode (target membership on), keep it out of git. Or set DIARY_SMS_PROVIDER=stub."
            )
        }

        applySMSLanguageCode()

        do {
            let verificationID = try await PhoneAuthProvider.provider()
                .verifyPhoneNumber(normalized, uiDelegate: nil)
            UserDefaults.standard.set(verificationID, forKey: Self.verificationIDDefaultsKey)
            return SMSCodeSession(
                verificationID: verificationID,
                phoneE164: normalized,
                stubDebugHint: nil
            )
        } catch {
            throw mapFirebaseError(error)
        }
    }

    func verify(code: String, session: SMSCodeSession) async throws -> SMSAuthUser {
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 4, trimmed.allSatisfy(\.isNumber) else {
            throw SMSAuthError.invalidCode
        }

        FirebaseBootstrap.configureIfNeeded()
        guard FirebaseApp.app() != nil else {
            throw SMSAuthError.notConfigured(
                "Firebase is not configured. Add GoogleService-Info.plist to the DiaryVault target."
            )
        }

        let verificationID = session.verificationID.isEmpty
            ? (UserDefaults.standard.string(forKey: Self.verificationIDDefaultsKey) ?? "")
            : session.verificationID
        guard !verificationID.isEmpty else {
            throw SMSAuthError.underlying("Missing verification session. Request a new code.")
        }

        let credential = PhoneAuthProvider.provider().credential(
            withVerificationID: verificationID,
            verificationCode: trimmed
        )

        do {
            let result = try await Auth.auth().signIn(with: credential)
            UserDefaults.standard.removeObject(forKey: Self.verificationIDDefaultsKey)
            let phone = result.user.phoneNumber ?? session.phoneE164
            return SMSAuthUser(userID: result.user.uid, phoneE164: phone)
        } catch {
            throw mapFirebaseError(error)
        }
    }

    private static let verificationIDDefaultsKey = "diary.firebase.authVerificationID"

    private func applySMSLanguageCode() {
        let stored = UserDefaults.standard.string(forKey: "appLanguage") ?? ""
        switch stored {
        case "zh-Hant":
            Auth.auth().languageCode = "zh-TW"
        case "zh-Hans":
            Auth.auth().languageCode = "zh-CN"
        default:
            Auth.auth().languageCode = "en"
        }
    }

    private func mapFirebaseError(_ error: Error) -> SMSAuthError {
        let ns = error as NSError
        let message = ns.localizedDescription.trimmingCharacters(in: .whitespacesAndNewlines)
        let lowered = message.lowercased()
        if let authCode = AuthErrorCode(rawValue: ns.code) {
            switch authCode {
            case .missingPhoneNumber, .invalidPhoneNumber:
                return .invalidPhone
            case .missingVerificationCode, .invalidVerificationCode, .sessionExpired:
                return .invalidCode
            case .tooManyRequests, .quotaExceeded:
                return .rateLimited
            case .operationNotAllowed:
                // Phone provider disabled, or operation blocked (often SMS region policy).
                if looksLikeRegionBlock(lowered) { return .regionNotAllowed }
                return .notConfigured(
                    "Phone sign-in is disabled in Firebase Console. Enable Authentication → Sign-in method → Phone."
                )
            case .appNotAuthorized, .invalidAppCredential, .captchaCheckFailed, .missingAppCredential:
                return .appVerificationFailed
            default:
                break
            }
        }

        if looksLikeRegionBlock(lowered) { return .regionNotAllowed }
        if lowered.contains("invalid phone") { return .invalidPhone }
        if lowered.contains("verification code") || lowered.contains("invalid code") {
            return .invalidCode
        }
        if lowered.contains("too many") || lowered.contains("quota") {
            return .rateLimited
        }
        if lowered.contains("app credential")
            || lowered.contains("recaptcha")
            || lowered.contains("app verification")
            || lowered.contains("captcha")
            || lowered.contains("invalid token") {
            return .appVerificationFailed
        }
        return .underlying(message.isEmpty ? "Firebase Auth error (\(ns.code))" : message)
    }

    private func looksLikeRegionBlock(_ lowered: String) -> Bool {
        lowered.contains("region")
            || lowered.contains("blocked")
            || lowered.contains("not authorized to send")
            || lowered.contains("cannot be used")
            || lowered.contains("sms cannot")
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
            "Twilio Verify client is not linked. Keep DIARY_SMS_PROVIDER=stub or firebase."
        )
    }

    func verify(code: String, session: SMSCodeSession) async throws -> SMSAuthUser {
        throw SMSAuthError.notConfigured(
            "Twilio Verify client is not linked."
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

    /// National significant number after stripping a leading trunk `0`.
    static func nationalDigits(from nationalNumber: String) -> String {
        let digits = nationalNumber.filter(\.isNumber)
        return digits.hasPrefix("0") ? String(digits.dropFirst()) : digits
    }

    /// Launch-region mobile lengths: TW 9, HK 8, CN 11 (after dropping a leading 0).
    static func isPlausibleNationalNumber(dial: RegionDial, nationalNumber: String) -> Bool {
        let national = nationalDigits(from: nationalNumber)
        switch dial {
        case .tw:
            return national.count == 9
        case .hk:
            return national.count == 8
        case .cn:
            return national.count == 11
        }
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
