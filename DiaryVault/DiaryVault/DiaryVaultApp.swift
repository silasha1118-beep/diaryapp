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
                .task {
                    diaryStore.bindCloudSync()
                }
                .onChange(of: account.userID) { _, _ in
                    // Sign-in / sign-out: pull+push when Firebase Auth is live; no-op when local-only.
                    diaryStore.scheduleSynchronize()
                }
                .onChange(of: account.firebaseAuthUID) { _, uid in
                    // Auth keychain restore can land after first launch sync no-op'd — re-run then.
                    if uid != nil {
                        diaryStore.scheduleSynchronize()
                    }
                }
                .onChange(of: account.firebaseAuthReady) { _, ready in
                    if ready {
                        diaryStore.scheduleSynchronize()
                    }
                }
                .onOpenURL { url in
                    // reCAPTCHA custom-scheme callback for Phone Auth (SwiftUI path).
                    if FirebaseApp.app() != nil {
                        _ = Auth.auth().canHandle(url)
                    }
                }
        }
    }
}
