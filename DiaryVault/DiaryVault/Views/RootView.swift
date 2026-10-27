import SwiftUI

struct RootView: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        Group {
            if appState.didCompleteOnboarding {
                MainTabView()
            } else {
                OnboardingView()
            }
        }
        .animation(.easeInOut(duration: 0.35), value: appState.didCompleteOnboarding)
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
