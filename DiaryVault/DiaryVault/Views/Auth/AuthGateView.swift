import SwiftUI

struct AuthGateView: View {
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var account: AccountSession
    @EnvironmentObject private var appState: AppState

    @State private var appeared = false

    var body: some View {
        ZStack {
            VaultBackground()

            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Spacer().frame(height: 24)

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

                    PhoneOTPLoginForm(chrome: .gate)

                    #if DEBUG
                    Divider()
                        .overlay(VaultTheme.line)
                        .padding(.vertical, 4)

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

                    Spacer().frame(height: 40)
                }
                .padding(.horizontal, 28)
            }
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.7)) { appeared = true }
        }
    }
}
