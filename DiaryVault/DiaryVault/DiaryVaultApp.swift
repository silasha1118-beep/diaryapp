import SwiftUI
import FirebaseCore
import FirebaseAuth

@main
struct DiaryVaultApp: App {
    @UIApplicationDelegateAdaptor(FirebaseAppDelegate.self) private var appDelegate

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
                .onOpenURL { url in
                    // reCAPTCHA custom-scheme callback for Phone Auth (SwiftUI path).
                    if FirebaseApp.app() != nil {
                        _ = Auth.auth().canHandle(url)
                    }
                }
        }
    }
}
