import Foundation
import Combine

/// Local account session for phone SMS OTP (primary) and optional Apple / DEBUG local.
/// No backend — credentials stay on device for this MVP gate.
@MainActor
final class AccountSession: ObservableObject {
    private let defaults = UserDefaults.standard

    private enum Keys {
        static let userID = "account.userID"
        static let email = "account.email"
        static let fullName = "account.fullName"
        static let phoneE164 = "account.phoneE164"
        static let provider = "account.provider"
    }

    enum Provider: String {
        case phone
        case apple
        case local
    }

    @Published private(set) var userID: String?
    @Published private(set) var email: String?
    @Published private(set) var fullName: String?
    @Published private(set) var phoneE164: String?
    @Published private(set) var provider: Provider?

    var isSignedIn: Bool { userID != nil }

    var displayLabel: String {
        if let fullName, !fullName.isEmpty { return fullName }
        if let phoneE164, !phoneE164.isEmpty {
            return PhoneNumberNormalizer.maskedDisplay(phoneE164)
        }
        if let email, !email.isEmpty { return email }
        if let userID { return String(userID.prefix(8)) + "…" }
        return ""
    }

    init() {
        userID = defaults.string(forKey: Keys.userID)
        email = defaults.string(forKey: Keys.email)
        fullName = defaults.string(forKey: Keys.fullName)
        phoneE164 = defaults.string(forKey: Keys.phoneE164)
        if let raw = defaults.string(forKey: Keys.provider) {
            provider = Provider(rawValue: raw)
        }
    }

    func signInWithPhone(phoneE164: String, displayName: String?) {
        let id = "phone-" + phoneE164.filter(\.isNumber)
        apply(
            userID: id,
            email: nil,
            fullName: displayName,
            phoneE164: phoneE164,
            provider: .phone
        )
    }

    func signInWithApple(userID: String, email: String?, fullName: String?) {
        apply(
            userID: userID,
            email: email,
            fullName: fullName,
            phoneE164: nil,
            provider: .apple
        )
    }

    /// DEBUG / simulator scaffold only — not a production auth path.
    func signInLocally(displayName: String?) {
        let id = "local-" + UUID().uuidString
        apply(
            userID: id,
            email: nil,
            fullName: displayName,
            phoneE164: nil,
            provider: .local
        )
    }

    func signOut() {
        userID = nil
        email = nil
        fullName = nil
        phoneE164 = nil
        provider = nil
        defaults.removeObject(forKey: Keys.userID)
        defaults.removeObject(forKey: Keys.email)
        defaults.removeObject(forKey: Keys.fullName)
        defaults.removeObject(forKey: Keys.phoneE164)
        defaults.removeObject(forKey: Keys.provider)
    }

    private func apply(
        userID: String,
        email: String?,
        fullName: String?,
        phoneE164: String?,
        provider: Provider
    ) {
        self.userID = userID
        self.email = email
        self.fullName = fullName
        self.phoneE164 = phoneE164
        self.provider = provider
        defaults.set(userID, forKey: Keys.userID)
        defaults.set(email, forKey: Keys.email)
        defaults.set(fullName, forKey: Keys.fullName)
        defaults.set(phoneE164, forKey: Keys.phoneE164)
        defaults.set(provider.rawValue, forKey: Keys.provider)
    }
}
