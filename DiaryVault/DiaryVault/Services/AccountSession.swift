import Foundation
import Combine

/// Local account session for Sign in with Apple (and DEBUG local vault).
/// No backend — credentials stay on device for this MVP gate.
@MainActor
final class AccountSession: ObservableObject {
    private let defaults = UserDefaults.standard

    private enum Keys {
        static let userID = "account.userID"
        static let email = "account.email"
        static let fullName = "account.fullName"
        static let provider = "account.provider"
    }

    enum Provider: String {
        case apple
        case local
    }

    @Published private(set) var userID: String?
    @Published private(set) var email: String?
    @Published private(set) var fullName: String?
    @Published private(set) var provider: Provider?

    var isSignedIn: Bool { userID != nil }

    var displayLabel: String {
        if let fullName, !fullName.isEmpty { return fullName }
        if let email, !email.isEmpty { return email }
        if let userID { return String(userID.prefix(8)) + "…" }
        return ""
    }

    init() {
        userID = defaults.string(forKey: Keys.userID)
        email = defaults.string(forKey: Keys.email)
        fullName = defaults.string(forKey: Keys.fullName)
        if let raw = defaults.string(forKey: Keys.provider) {
            provider = Provider(rawValue: raw)
        }
    }

    func signInWithApple(userID: String, email: String?, fullName: String?) {
        apply(userID: userID, email: email, fullName: fullName, provider: .apple)
    }

    /// DEBUG / simulator scaffold only — not a production auth path.
    func signInLocally(displayName: String?) {
        let id = "local-" + UUID().uuidString
        apply(userID: id, email: nil, fullName: displayName, provider: .local)
    }

    func signOut() {
        userID = nil
        email = nil
        fullName = nil
        provider = nil
        defaults.removeObject(forKey: Keys.userID)
        defaults.removeObject(forKey: Keys.email)
        defaults.removeObject(forKey: Keys.fullName)
        defaults.removeObject(forKey: Keys.provider)
    }

    private func apply(userID: String, email: String?, fullName: String?, provider: Provider) {
        self.userID = userID
        self.email = email
        self.fullName = fullName
        self.provider = provider
        defaults.set(userID, forKey: Keys.userID)
        defaults.set(email, forKey: Keys.email)
        defaults.set(fullName, forKey: Keys.fullName)
        defaults.set(provider.rawValue, forKey: Keys.provider)
    }
}
