import SwiftUI
import AuthenticationServices

struct AuthGateView: View {
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var account: AccountSession
    @EnvironmentObject private var appState: AppState

    @State private var errorMessage: String?
    @State private var appeared = false

    var body: some View {
        ZStack {
            VaultBackground()

            VStack(alignment: .leading, spacing: 24) {
                Spacer()

                Text(localization.text("brand.name"))
                    .font(.system(size: 36, weight: .semibold, design: .serif))
                    .foregroundStyle(VaultTheme.ink)
                    .opacity(appeared ? 1 : 0)
                    .offset(y: appeared ? 0 : 10)

                Text(localization.text("auth.headline"))
                    .font(.system(size: 22, weight: .regular, design: .serif))
                    .foregroundStyle(VaultTheme.ink)
                    .fixedSize(horizontal: false, vertical: true)

                Text(localization.text("auth.body"))
                    .font(.system(size: 16))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .lineSpacing(4)
                    .fixedSize(horizontal: false, vertical: true)

                SignInWithAppleButton(.signIn) { request in
                    request.requestedScopes = [.fullName, .email]
                } onCompletion: { result in
                    handleApple(result)
                }
                .signInWithAppleButtonStyle(.black)
                .frame(height: 48)
                .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))

                if let errorMessage {
                    Text(errorMessage)
                        .font(.system(size: 14))
                        .foregroundStyle(VaultTheme.seal)
                }

                #if DEBUG
                Button {
                    account.signInLocally(displayName: appState.displayName.isEmpty ? nil : appState.displayName)
                } label: {
                    Text(localization.text("auth.devLocal"))
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(VaultTheme.inkMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                }
                .buttonStyle(.plain)
                #endif

                Spacer()
            }
            .padding(.horizontal, 28)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.7)) { appeared = true }
        }
    }

    private func handleApple(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .failure(let error):
            let ns = error as NSError
            if ns.domain == ASAuthorizationError.errorDomain,
               ns.code == ASAuthorizationError.canceled.rawValue {
                errorMessage = nil
                return
            }
            errorMessage = localization.text("auth.error")
        case .success(let authorization):
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
                errorMessage = localization.text("auth.error")
                return
            }
            let fullName = PersonNameComponentsFormatter().string(from: credential.fullName ?? PersonNameComponents())
            let trimmedName = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
            account.signInWithApple(
                userID: credential.user,
                email: credential.email,
                fullName: trimmedName.isEmpty ? nil : trimmedName
            )
            if let name = account.fullName, !name.isEmpty, appState.displayName.isEmpty {
                appState.displayName = name
            }
            errorMessage = nil
        }
    }
}
