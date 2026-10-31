import SwiftUI

/// Post-login display-name step before `MainTabView` when name is empty.
/// Kept deliberately plain (Form / TextField / Button) — complex wrappers + animation
/// have crashed the Swift type checker (VarDecl assertion) on Silas's Xcode.
struct PostLoginDisplayNameView: View {
    @EnvironmentObject private var localization: LocalizationManager
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var account: AccountSession

    @State private var name: String = ""
    @State private var isSaving: Bool = false

    var body: some View {
        Form {
            Section {
                Text(localization.text("brand.name"))
                Text(localization.text("auth.displayName.title"))
                Text(localization.text("auth.displayName.body"))
            }

            Section {
                TextField(localization.text("auth.displayName.placeholder"), text: $name)
            }

            Section {
                Button(localization.text("auth.displayName.save")) {
                    saveTapped()
                }
                .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSaving)

                Button(localization.text("auth.displayName.skip")) {
                    skipTapped()
                }
                .disabled(isSaving)
            }
        }
    }

    private func saveTapped() {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        isSaving = true
        appState.displayName = trimmed
        let uid = account.firebaseAuthUID ?? account.userID
        if let uid, !uid.isEmpty {
            appState.clearDisplayNamePromptSkip(for: uid)
        }
        Task {
            await account.updateDisplayName(trimmed)
            await MainActor.run { isSaving = false }
        }
    }

    private func skipTapped() {
        let uid = account.firebaseAuthUID ?? account.userID
        if let uid, !uid.isEmpty {
            appState.markDisplayNamePromptSkipped(for: uid)
        }
    }
}

typealias DisplayNamePromptSheet = PostLoginDisplayNameView
