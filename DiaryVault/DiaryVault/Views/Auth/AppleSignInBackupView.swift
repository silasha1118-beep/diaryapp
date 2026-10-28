import SwiftUI
import AuthenticationServices

/// Optional Sign in with Apple backup under the primary phone SMS OTP form.
/// Product rule: SMS is primary; Apple is backup (paid Apple Developer required).
struct AppleSignInBackupView: View {
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var account: AccountSession
    @EnvironmentObject private var appState: AppState

    var chrome: PhoneOTPLoginForm.Chrome = .gate

    @State private var expanded = false
    @State private var errorMessage: String?
    @State private var isWorking = false

    var body: some View {
        VStack(alignment: .leading, spacing: chrome == .gate ? 12 : 10) {
            Button {
                withAnimation(.easeInOut(duration: 0.25)) {
                    expanded.toggle()
                }
            } label: {
                Text(localization.text("auth.apple.backup"))
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(VaultTheme.inkMuted)
            }
            .buttonStyle(.plain)

            if expanded {
                SignInWithAppleButton(.signIn) { request in
                    AppleSignInCoordinator.prepareRequest(request)
                } onCompletion: { result in
                    Task { await complete(result) }
                }
                .signInWithAppleButtonStyle(.black)
                .frame(height: 48)
                .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
                .disabled(isWorking)
                .opacity(isWorking ? 0.6 : 1)

                Text(localization.text("auth.apple.paidNote"))
                    .font(.system(size: 12))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .fixedSize(horizontal: false, vertical: true)

                if let errorMessage {
                    Text(errorMessage)
                        .font(.system(size: 14))
                        .foregroundStyle(VaultTheme.seal)
                }
            }
        }
    }

    @MainActor
    private func complete(_ result: Result<ASAuthorization, Error>) async {
        errorMessage = nil
        isWorking = true
        defer { isWorking = false }

        do {
            try await AppleSignInCoordinator.handle(
                result: result,
                account: account,
                preferredDisplayName: appState.displayName.isEmpty ? nil : appState.displayName
            )
            if let name = account.fullName, !name.isEmpty, appState.displayName.isEmpty {
                appState.displayName = name
            }
        } catch let error as AppleSignInError {
            if case .canceled = error { return }
            errorMessage = localization.text("auth.apple.error")
        } catch {
            errorMessage = localization.text("auth.apple.error")
        }
    }
}
