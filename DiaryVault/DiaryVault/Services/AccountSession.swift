import Foundation
import Combine
import FirebaseCore
import FirebaseAuth

/// Local account session for phone SMS OTP (primary) and DEBUG local.
/// When Firebase Phone Auth succeeds, `userID` is the Firebase UID; diary data stays on device.
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
        /// Legacy sessions from when Sign in with Apple was offered; no longer a sign-in path.
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

        // Prefer live Firebase session when the SDK is configured and still signed in.
        FirebaseBootstrap.configureIfNeeded()
        if FirebaseApp.app() != nil, let fbUser = Auth.auth().currentUser {
            let phone = fbUser.phoneNumber ?? phoneE164
            apply(
                userID: fbUser.uid,
                email: fbUser.email ?? email,
                fullName: fullName,
                phoneE164: phone,
                provider: .phone
            )
        }
    }

    func signInWithPhone(userID: String, phoneE164: String, displayName: String?) {
        apply(
            userID: userID,
            email: nil,
            fullName: displayName,
            phoneE164: phoneE164,
            provider: .phone
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
        if FirebaseApp.app() != nil {
            try? Auth.auth().signOut()
        }
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
