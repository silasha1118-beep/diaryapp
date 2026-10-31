import SwiftUI
import FirebaseAuth

struct RootView: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var account: AccountSession

    var body: some View {
        Group {
            if !appState.didCompleteOnboarding {
                OnboardingView()
            } else if !account.isSignedIn {
                AuthGateView()
            } else if needsPostLoginDisplayName {
                PostLoginDisplayNameView()
            } else {
                MainTabView()
            }
        }
        .animation(.easeInOut(duration: 0.35), value: appState.didCompleteOnboarding)
        .animation(.easeInOut(duration: 0.35), value: account.isSignedIn)
        .animation(.easeInOut(duration: 0.35), value: needsPostLoginDisplayName)
        .onAppear { seedDisplayNameFromExistingIdentity() }
        .onChange(of: account.userID) { _, _ in
            seedDisplayNameFromExistingIdentity()
        }
        .onChange(of: account.firebaseAuthUID) { _, _ in
            seedDisplayNameFromExistingIdentity()
        }
        .onChange(of: account.fullName) { _, _ in
            seedDisplayNameFromExistingIdentity()
        }
    }

    /// True for new phone **or** Apple accounts with no name yet. Existing named users skip.
    private var needsPostLoginDisplayName: Bool {
        guard account.isSignedIn else { return false }
        if hasResolvedDisplayName { return false }
        let uid = account.firebaseAuthUID ?? account.userID
        guard let uid, !uid.isEmpty else { return false }
        if appState.didSkipDisplayNamePrompt(for: uid) { return false }
        return true
    }

    private var hasResolvedDisplayName: Bool {
        let local = appState.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        if !local.isEmpty { return true }
        let session = (account.fullName ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !session.isEmpty { return true }
        let auth = (Auth.auth().currentUser?.displayName ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return !auth.isEmpty
    }

    /// Returning users / Apple-provided names: copy into local Settings identity and skip the gate.
    private func seedDisplayNameFromExistingIdentity() {
        guard account.isSignedIn else { return }
        guard appState.displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }

        if let session = account.fullName?.trimmingCharacters(in: .whitespacesAndNewlines), !session.isEmpty {
            appState.displayName = session
            return
        }
        if let auth = Auth.auth().currentUser?.displayName?
            .trimmingCharacters(in: .whitespacesAndNewlines),
           !auth.isEmpty {
            appState.displayName = auth
            if account.fullName == nil || account.fullName?.isEmpty == true {
                Task { await account.updateDisplayName(auth) }
            }
        }
    }
}

struct MainTabView: View {
    @EnvironmentObject private var localization: LocalizationManager
    @State private var tab = 0

    var body: some View {
        TabView(selection: $tab) {
            DiaryListView()
                .tabItem {
                    Label(localization.text("tab.vault"), systemImage: "book.closed.fill")
                }
                .tag(0)

            MirrorChatView()
                .tabItem {
                    Label(localization.text("tab.mirror"), systemImage: "circle.lefthalf.filled")
                }
                .tag(1)

            SettingsView()
                .tabItem {
                    Label(localization.text("tab.settings"), systemImage: "gearshape")
                }
                .tag(2)
        }
        .tint(VaultTheme.seal)
    }
}
