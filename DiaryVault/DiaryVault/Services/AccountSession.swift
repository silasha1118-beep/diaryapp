import Foundation
import Combine
import FirebaseCore
import FirebaseAuth

/// Local account session for phone SMS OTP (primary), optional Sign in with Apple backup, and DEBUG local.
/// When Firebase Auth succeeds, `userID` is the Firebase UID. Text diary syncs to Firestore while signed in;
/// signed-out / DEBUG local sessions stay on-device only.
@MainActor
final class AccountSession: ObservableObject {
    private let defaults = UserDefaults.standard
    /// Retained for app lifetime so Auth restore callbacks keep firing.
    private var authStateHandle: AuthStateDidChangeListenerHandle?

    private enum Keys {
        static let userID = "account.userID"
        static let email = "account.email"
        static let fullName = "account.fullName"
        static let phoneE164 = "account.phoneE164"
        static let provider = "account.provider"
    }

    enum Provider: String {
        case phone
        /// Optional backup next to phone SMS (requires paid Apple Developer + capability).
        case apple
        case local
    }

    @Published private(set) var userID: String?
    @Published private(set) var email: String?
    @Published private(set) var fullName: String?
    @Published private(set) var phoneE164: String?
    @Published private(set) var provider: Provider?
    /// Live Firebase Auth UID once the SDK finishes restoring keychain state (nil for stub / local).
    @Published private(set) var firebaseAuthUID: String?
    /// Becomes true after the first Auth state callback (or immediately when Firebase is not configured).
    @Published private(set) var firebaseAuthReady: Bool = false

    var isSignedIn: Bool { userID != nil }

    /// Firestore sync requires a live Firebase Auth user — not merely a UserDefaults / stub session.
    var hasLiveFirebaseAuth: Bool {
        guard let firebaseAuthUID, !firebaseAuthUID.isEmpty else { return false }
        return true
    }

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
        // Auth may restore asynchronously from the keychain — do not rely on a one-shot
        // `currentUser` read alone (that was silently skipping Firestore sync).
        FirebaseBootstrap.configureIfNeeded()
        if FirebaseApp.app() != nil {
            if let fbUser = Auth.auth().currentUser {
                applyFirebaseUser(fbUser)
            }
            authStateHandle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
                Task { @MainActor in
                    self?.handleFirebaseAuthStateChange(user)
                }
            }
        } else {
            firebaseAuthReady = true
            firebaseAuthUID = nil
        }
    }

    private func handleFirebaseAuthStateChange(_ user: User?) {
        firebaseAuthReady = true
        if let user {
            applyFirebaseUser(user)
            return
        }
        // First callback can briefly be nil while keychain restore is in flight — do not
        // wipe a UserDefaults session on that alone. Only clear the live-UID mirror.
        firebaseAuthUID = nil
    }

    private func applyFirebaseUser(_ fbUser: User) {
        firebaseAuthUID = fbUser.uid
        let usesApple = fbUser.providerData.contains { $0.providerID == "apple.com" }
        let phone = fbUser.phoneNumber ?? phoneE164
        apply(
            userID: fbUser.uid,
            email: fbUser.email ?? email,
            fullName: fullName,
            phoneE164: usesApple ? nil : phone,
            provider: usesApple ? .apple : .phone
        )
    }

    func signInWithPhone(userID: String, phoneE164: String, displayName: String?) {
        apply(
            userID: userID,
            email: nil,
            fullName: displayName,
            phoneE164: phoneE164,
            provider: .phone
        )
        // Mirror live Auth immediately when OTP went through Firebase (stub leaves this nil).
        if Auth.auth().currentUser?.uid == userID {
            firebaseAuthUID = userID
        } else {
            firebaseAuthUID = nil
        }
    }

    /// Optional backup path — AuthenticationServices (and Firebase Auth when plist is present).
    func signInWithApple(userID: String, email: String?, fullName: String?) {
        apply(
            userID: userID,
            email: email,
            fullName: fullName,
            phoneE164: nil,
            provider: .apple
        )
        if Auth.auth().currentUser?.uid == userID {
            firebaseAuthUID = userID
        } else {
            firebaseAuthUID = nil
        }
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
        firebaseAuthUID = nil
        clearLocalSession()
    }

    /// App Store Guideline 5.1.1(v): apps with account creation must offer account deletion.
    /// Clears the local session and diary is wiped by the caller. Attempts Firebase Auth
    /// `delete()` when a live user exists; if Firebase requires recent login, local wipe still
    /// proceeds and `requiresReauthentication` is returned so UI can show a re-auth hint.
    @discardableResult
    func deleteAccount() async -> DeleteAccountOutcome {
        var outcome: DeleteAccountOutcome = .localOnly
        if FirebaseApp.app() != nil, let user = Auth.auth().currentUser {
            do {
                try await user.delete()
                outcome = .deletedRemote
            } catch {
                let ns = error as NSError
                let needsRecentLogin =
                    ns.domain == AuthErrorDomain
                    && ns.code == AuthErrorCode.requiresRecentLogin.rawValue
                try? Auth.auth().signOut()
                outcome = needsRecentLogin ? .requiresReauthentication : .remoteFailed
            }
        }
        firebaseAuthUID = nil
        clearLocalSession()
        return outcome
    }

    enum DeleteAccountOutcome {
        case localOnly
        case deletedRemote
        case requiresReauthentication
        case remoteFailed
    }

    private func clearLocalSession() {
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
