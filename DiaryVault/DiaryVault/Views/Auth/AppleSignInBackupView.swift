import SwiftUI
import AuthenticationServices

/// Sign in with Apple under the primary phone SMS OTP form, or link Apple to the current UID.
struct AppleSignInBackupView: View {
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var account: AccountSession
    @EnvironmentObject private var appState: AppState

    var chrome: PhoneOTPLoginForm.Chrome = .gate
    var mode: PhoneOTPLoginForm.Mode = .signIn
    var onCompleted: (() -> Void)? = nil

    @State private var errorMessage: String?
    @State private var infoMessage: String?
    @State private var isWorking = false

    var body: some View {
        VStack(alignment: .leading, spacing: chrome == .gate ? 12 : 10) {
            // Settings link section owns the 「連結 Apple」header — keep in-form title for gate only.
            if !(mode == .link && chrome == .settings) {
                Text(
                    mode == .link
                        ? localization.text("settings.account.link.apple.title")
                        : localization.text("auth.apple.backup")
                )
                .font(.system(size: chrome == .gate ? 15 : 14, weight: .semibold))
                .foregroundStyle(VaultTheme.ink)
                .accessibilityAddTraits(.isHeader)
            }

            SignInWithAppleButton(mode == .link ? .continue : .signIn) { request in
                AppleSignInCoordinator.prepareRequest(request)
            } onCompletion: { result in
                Task { await complete(result) }
            }
            .signInWithAppleButtonStyle(.black)
            .frame(maxWidth: .infinity)
            .frame(height: chrome == .gate ? 52 : 48)
            .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
            .disabled(isWorking)
            .opacity(isWorking ? 0.6 : 1)
            .accessibilityLabel(
                mode == .link
                    ? localization.text("settings.account.link.apple.title")
                    : localization.text("auth.apple.backup")
            )

            if !(mode == .link && chrome == .settings) {
                Text(
                    mode == .link
                        ? localization.text("settings.account.link.apple.note")
                        : localization.text("auth.apple.secondaryNote")
                )
                .font(.system(size: 12))
                .foregroundStyle(VaultTheme.inkMuted)
                .fixedSize(horizontal: false, vertical: true)
            }

            if let infoMessage {
                Text(infoMessage)
                    .font(.system(size: 13))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if let errorMessage {
                Text(errorMessage)
                    .font(.system(size: 14))
                    .foregroundStyle(VaultTheme.seal)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    @MainActor
    private func complete(_ result: Result<ASAuthorization, Error>) async {
        errorMessage = nil
        infoMessage = nil
        isWorking = true
        defer { isWorking = false }

        do {
            try await AppleSignInCoordinator.handle(
                result: result,
                account: account,
                preferredDisplayName: appState.displayName.isEmpty ? nil : appState.displayName,
                mode: mode == .link ? .link : .signIn
            )
            if let name = account.fullName, !name.isEmpty, appState.displayName.isEmpty {
                appState.displayName = name
            }
            if mode == .link {
                infoMessage = localization.text("settings.account.link.apple.success")
            }
            onCompleted?()
        } catch let error as AppleSignInError {
            if case .canceled = error { return }
            errorMessage = mapAppleError(error)
        } catch {
            errorMessage = localization.text("auth.apple.error")
        }
    }

    private func mapAppleError(_ error: AppleSignInError) -> String {
        switch error {
        case .canceled:
            return ""
        case .credentialAlreadyInUse:
            return localization.text("settings.account.link.apple.alreadyInUse")
                + "\n"
                + localization.text("settings.account.link.mergeGuidance")
        case .providerAlreadyLinked:
            return localization.text("settings.account.link.apple.alreadyLinked")
        case .notSignedInForLink:
            return localization.text("settings.account.link.needSignedIn")
        default:
            return localization.text(
                mode == .link
                    ? "settings.account.link.apple.error"
                    : "auth.apple.error"
            )
        }
    }
}
