import SwiftUI

@main
struct DiaryVaultApp: App {
    @StateObject private var appState = AppState()
    @StateObject private var diaryStore = DiaryStore()
    @StateObject private var localization = LocalizationManager()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(appState)
                .environmentObject(diaryStore)
                .environmentObject(localization)
                .environment(\.locale, localization.locale)
                .preferredColorScheme(.light)
        }
    }
}
