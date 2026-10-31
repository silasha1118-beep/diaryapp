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
    /// True when `providerData` (or `phoneNumber`) includes phone — may coexist with Apple.
    @Published private(set) var hasLinkedPhone: Bool = false
    /// True when `providerData` includes `apple.com` — may coexist with phone.
    @Published private(set) var hasLinkedApple: Bool = false

    var isSignedIn: Bool { userID != nil }

    /// Needs Settings 「連結手機」 when signed in via Apple (or other) without a phone provider.
    var needsPhoneLink: Bool {
        hasLiveFirebaseAuth && isSignedIn && !hasLinkedPhone
    }

    /// Needs Settings 「連結 Apple」 when signed in via phone without Apple.
    var needsAppleLink: Bool {
        hasLiveFirebaseAuth && isSignedIn && !hasLinkedApple
    }

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
        // Provisional until Auth restores; live providerData overwrites these.
        hasLinkedPhone = provider == .phone || phoneE164 != nil
        hasLinkedApple = provider == .apple

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
        let linkedPhone = Self.userHasPhoneProvider(fbUser)
        let linkedApple = Self.userHasAppleProvider(fbUser)
        hasLinkedPhone = linkedPhone
        hasLinkedApple = linkedApple

        let phone = fbUser.phoneNumber ?? (linkedPhone ? phoneE164 : nil)
        let resolvedProvider: Provider = {
            if linkedPhone && linkedApple {
                // Keep last primary when both are linked; default to phone.
                if provider == .apple { return .apple }
                return .phone
            }
            if linkedApple { return .apple }
            if linkedPhone { return .phone }
            return provider ?? .phone
        }()
        apply(
            userID: fbUser.uid,
            email: fbUser.email ?? email,
            fullName: fullName ?? fbUser.displayName,
            phoneE164: phone,
            provider: resolvedProvider
        )
        scheduleUserProfileUpsert(uid: fbUser.uid, displayName: fullName ?? fbUser.displayName)
    }

    /// After `link(with:)` succeeds — reload Auth user, refresh session flags, upsert `users/{uid}`.
    func refreshAfterProviderLink(displayName: String? = nil) async {
        guard let user = Auth.auth().currentUser else { return }
        do {
            try await user.reload()
        } catch {
            #if DEBUG
            print("Auth reload after link failed: \(error)")
            #endif
        }
        guard let refreshed = Auth.auth().currentUser else { return }
        let name = displayName ?? fullName ?? refreshed.displayName
        applyFirebaseUser(refreshed)
        if let name, !name.isEmpty {
            fullName = name
            defaults.set(name, forKey: Keys.fullName)
        }
        // Force profile sync so phoneNumber / appleUserId / authProviders land on users/{uid}.
        do {
            try await DiaryCloudSync.upsertUserProfile(uid: refreshed.uid, displayName: name)
        } catch {
            #if DEBUG
            print("User profile upsert after link failed: \(error)")
            #endif
        }
    }

    static func userHasPhoneProvider(_ user: User) -> Bool {
        if user.phoneNumber != nil { return true }
        return user.providerData.contains { $0.providerID == "phone" }
    }

    static func userHasAppleProvider(_ user: User) -> Bool {
        user.providerData.contains { $0.providerID == "apple.com" }
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
        if let current = Auth.auth().currentUser, current.uid == userID {
            firebaseAuthUID = userID
            hasLinkedPhone = Self.userHasPhoneProvider(current)
            hasLinkedApple = Self.userHasAppleProvider(current)
            scheduleUserProfileUpsert(uid: userID, displayName: displayName)
        } else {
            firebaseAuthUID = nil
            hasLinkedPhone = true
            // Keep any previously known Apple flag only if same local session — fresh phone sign-in is phone-only.
            hasLinkedApple = false
        }
    }

    /// Optional backup path — AuthenticationServices (and Firebase Auth when plist is present).
    func signInWithApple(userID: String, email: String?, fullName: String?) {
        let linkedPhoneFromAuth: String? = {
            guard let current = Auth.auth().currentUser, current.uid == userID else { return nil }
            return current.phoneNumber
        }()
        apply(
            userID: userID,
            email: email,
            fullName: fullName,
            phoneE164: linkedPhoneFromAuth,
            provider: .apple
        )
        if let current = Auth.auth().currentUser, current.uid == userID {
            firebaseAuthUID = userID
            hasLinkedPhone = Self.userHasPhoneProvider(current)
            hasLinkedApple = Self.userHasAppleProvider(current)
            scheduleUserProfileUpsert(uid: userID, displayName: fullName)
        } else {
            firebaseAuthUID = nil
            hasLinkedApple = true
            hasLinkedPhone = false
        }
    }

    /// Persists display name to local session, Firebase Auth profile, and `users/{uid}.displayName`.
    func updateDisplayName(_ name: String) async {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        fullName = trimmed
        defaults.set(trimmed, forKey: Keys.fullName)
        guard let user = Auth.auth().currentUser else { return }
        let change = user.createProfileChangeRequest()
        change.displayName = trimmed
        do {
            try await change.commitChanges()
        } catch {
            #if DEBUG
            print("Auth displayName update failed: \(error)")
            #endif
        }
        do {
            try await DiaryCloudSync.upsertUserProfile(uid: user.uid, displayName: trimmed)
        } catch {
            #if DEBUG
            print("User profile displayName upsert failed: \(error)")
            #endif
        }
    }

    /// Best-effort Firestore `users/{uid}` identity upsert (phone / Apple readable in Console).
    private func scheduleUserProfileUpsert(uid: String, displayName: String?) {
        Task {
            if let displayName, !displayName.isEmpty,
               let user = Auth.auth().currentUser, user.uid == uid,
               user.displayName == nil || user.displayName?.isEmpty == true {
                let change = user.createProfileChangeRequest()
                change.displayName = displayName
                try? await change.commitChanges()
            }
            do {
                try await DiaryCloudSync.upsertUserProfile(uid: uid, displayName: displayName)
            } catch {
                #if DEBUG
                print("User profile upsert failed: \(error)")
                #endif
            }
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
                let needsRecentLogin = FirebaseAuthErrorMap.matches(ns, .requiresRecentLogin)
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
        hasLinkedPhone = false
        hasLinkedApple = false
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
