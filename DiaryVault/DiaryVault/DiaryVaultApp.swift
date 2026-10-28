import SwiftUI

@main
struct DiaryVaultApp: App {
    @StateObject private var appState = AppState()
    @StateObject private var diaryStore = DiaryStore()
    @StateObject private var localization = LocalizationManager()
    @StateObject private var account = AccountSession()
    @StateObject private var subscriptions = SubscriptionStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(appState)
                .environmentObject(diaryStore)
                .environmentObject(localization)
                .environmentObject(account)
                .environmentObject(subscriptions)
                .environment(\.locale, localization.locale)
                .preferredColorScheme(.light)
        }
    }
}
