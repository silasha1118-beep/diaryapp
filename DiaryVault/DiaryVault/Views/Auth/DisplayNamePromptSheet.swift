import SwiftUI

/// Post-login display-name onboarding for **every** new account (phone or Apple).
/// Shown as a blocking step before `MainTabView` when local / Auth displayName are empty.
/// Existing users who already have a name (Auth, session, or local) never see this.
struct PostLoginDisplayNameView: View {
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var account: AccountSession

    @State private var name = ""
    @State private var isSaving = false
    @State private var appeared = false

    var body: some View {
        ZStack {
            VaultBackground()

            VStack(alignment: .leading, spacing: 20) {
                Spacer().frame(height: 24)

                Text(localization.text("brand.name"))
                    .font(.system(size: 32, weight: .semibold, design: .serif))
                    .foregroundStyle(VaultTheme.ink)
                    .opacity(appeared ? 1 : 0)
                    .offset(y: appeared ? 0 : 10)

                Text(localization.text("auth.displayName.title"))
                    .font(.system(size: 24, weight: .regular, design: .serif))
                    .foregroundStyle(VaultTheme.ink)
                    .fixedSize(horizontal: false, vertical: true)

                Text(localization.text("auth.displayName.body"))
                    .font(.system(size: 16))
                    .foregroundStyle(VaultTheme.inkMuted)
                    .lineSpacing(4)
                    .fixedSize(horizontal: false, vertical: true)

                TextField(localization.text("auth.displayName.placeholder"), text: $name)
                    .textContentType(.name)
                    .autocorrectionDisabled()
                    .padding(.horizontal, 12)
                    .padding(.vertical, 14)
                    .background(VaultTheme.paper.opacity(0.9))
                    .overlay(
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .stroke(VaultTheme.line, lineWidth: 1)
                    )

                Button {
                    Task { await save() }
                } label: {
                    HStack {
                        if isSaving { ProgressView().tint(.white) }
                        Text(localization.text("auth.displayName.save"))
                            .font(.system(size: 16, weight: .semibold))
                    }
                    .foregroundStyle(Color.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(VaultTheme.seal.opacity(canSave ? 1 : 0.4))
                    .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
                }
                .disabled(!canSave || isSaving)
                .buttonStyle(.plain)

                Button {
                    skip()
                } label: {
                    Text(localization.text("auth.displayName.skip"))
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(VaultTheme.inkMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                }
                .buttonStyle(.plain)
                .disabled(isSaving)

                Spacer()
            }
            .padding(.horizontal, 28)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.6)) { appeared = true }
        }
    }

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    @MainActor
    private func save() async {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        isSaving = true
        defer { isSaving = false }
        appState.displayName = trimmed
        if let uid = account.firebaseAuthUID ?? account.userID {
            appState.clearDisplayNamePromptSkip(for: uid)
        }
        await account.updateDisplayName(trimmed)
    }

    private func skip() {
        if let uid = account.firebaseAuthUID ?? account.userID {
            appState.markDisplayNamePromptSkipped(for: uid)
        }
    }
}

/// Kept name for any call sites that still reference the sheet type alias.
typealias DisplayNamePromptSheet = PostLoginDisplayNameView
