import Foundation
import Security
import AuthenticationServices
import CryptoKit
import FirebaseCore
import FirebaseAuth

/// Errors from the optional Sign in with Apple backup path.
enum AppleSignInError: LocalizedError, Equatable {
    case canceled
    case invalidCredential
    case firebase(String)
    case underlying(String)

    var errorDescription: String? {
        switch self {
        case .canceled: return nil
        case .invalidCredential: return "Invalid Apple ID credential"
        case .firebase(let message): return message
        case .underlying(let message): return message
        }
    }
}

/// Builds the Sign in with Apple request (nonce) and completes local / Firebase session.
enum AppleSignInCoordinator {
    /// Random nonce for the current Apple authorization attempt (Firebase requires the raw value).
    @MainActor
    private static var currentNonce: String?

    /// Call before presenting `SignInWithAppleButton` / `ASAuthorizationAppleIDRequest`.
    @MainActor
    static func prepareRequest(_ request: ASAuthorizationAppleIDRequest) {
        let nonce = randomNonceString()
        currentNonce = nonce
        request.requestedScopes = [.fullName, .email]
        request.nonce = sha256(nonce)
    }

    /// Handles the button / controller completion. Updates `AccountSession` on success.
    @MainActor
    static func handle(
        result: Result<ASAuthorization, Error>,
        account: AccountSession,
        preferredDisplayName: String?
    ) async throws {
        switch result {
        case .failure(let error):
            let ns = error as NSError
            if ns.domain == ASAuthorizationError.errorDomain,
               ns.code == ASAuthorizationError.canceled.rawValue {
                throw AppleSignInError.canceled
            }
            throw AppleSignInError.underlying(ns.localizedDescription)

        case .success(let authorization):
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
                throw AppleSignInError.invalidCredential
            }

            let formatter = PersonNameComponentsFormatter()
            let appleName = formatter.string(from: credential.fullName ?? PersonNameComponents())
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let resolvedName: String? = {
                if !appleName.isEmpty { return appleName }
                if let preferredDisplayName, !preferredDisplayName.isEmpty { return preferredDisplayName }
                return nil
            }()

            FirebaseBootstrap.configureIfNeeded()
            if FirebaseBootstrap.hasGoogleServiceInfoPlist, FirebaseApp.app() != nil {
                try await signInWithFirebase(
                    credential: credential,
                    account: account,
                    fullName: resolvedName
                )
            } else {
                account.signInWithApple(
                    userID: credential.user,
                    email: credential.email,
                    fullName: resolvedName
                )
            }
            currentNonce = nil
        }
    }

    // MARK: - Firebase Auth

    @MainActor
    private static func signInWithFirebase(
        credential: ASAuthorizationAppleIDCredential,
        account: AccountSession,
        fullName: String?
    ) async throws {
        guard let nonce = currentNonce else {
            throw AppleSignInError.firebase("Missing Apple sign-in nonce. Try again.")
        }
        guard let tokenData = credential.identityToken,
              let idToken = String(data: tokenData, encoding: .utf8) else {
            throw AppleSignInError.firebase("Unable to fetch Apple identity token.")
        }

        let oauth = OAuthProvider.appleCredential(
            withIDToken: idToken,
            rawNonce: nonce,
            fullName: credential.fullName
        )

        do {
            let result = try await Auth.auth().signIn(with: oauth)
            let email = result.user.email ?? credential.email
            // Persist displayName on the Auth user so users/{uid}.displayName can be filled from Auth.
            if let fullName, !fullName.isEmpty,
               result.user.displayName == nil || result.user.displayName?.isEmpty == true {
                let change = result.user.createProfileChangeRequest()
                change.displayName = fullName
                try? await change.commitChanges()
            }
            account.signInWithApple(
                userID: result.user.uid,
                email: email,
                fullName: fullName
            )
        } catch {
            let message = (error as NSError).localizedDescription
            throw AppleSignInError.firebase(
                message.isEmpty ? "Firebase Apple sign-in failed." : message
            )
        }
    }

    // MARK: - Nonce helpers (Firebase Auth docs)

    private static func randomNonceString(length: Int = 32) -> String {
        precondition(length > 0)
        var randomBytes = [UInt8](repeating: 0, count: length)
        let status = SecRandomCopyBytes(kSecRandomDefault, randomBytes.count, &randomBytes)
        if status != errSecSuccess {
            // Extremely rare — fall back so the request can still proceed locally.
            return UUID().uuidString.replacingOccurrences(of: "-", with: "")
        }
        let charset: [Character] = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        return String(randomBytes.map { charset[Int($0) % charset.count] })
    }

    private static func sha256(_ input: String) -> String {
        let data = Data(input.utf8)
        let hash = SHA256.hash(data: data)
        return hash.map { String(format: "%02x", $0) }.joined()
    }
}
