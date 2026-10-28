import Foundation
import UIKit
import FirebaseCore
import FirebaseAuth

/// Bridges APNs + reCAPTCHA URL callbacks into Firebase Auth.
/// Required for SwiftUI apps (Auth docs: pass token / notification / URL explicitly).
final class FirebaseAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseBootstrap.configureIfNeeded()
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        guard FirebaseApp.app() != nil else { return }
        Auth.auth().setAPNSToken(deviceToken, type: .unknown)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Simulator / missing APNs key: Firebase falls back to reCAPTCHA — not fatal.
        #if DEBUG
        print("APNs registration failed (reCAPTCHA fallback OK): \(error.localizedDescription)")
        #endif
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        if FirebaseApp.app() != nil, Auth.auth().canHandleNotification(userInfo) {
            completionHandler(.noData)
            return
        }
        completionHandler(.noData)
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        guard FirebaseApp.app() != nil else { return false }
        return Auth.auth().canHandle(url)
    }
}

/// Safe Firebase init — only when `GoogleService-Info.plist` is in the app bundle.
enum FirebaseBootstrap {
    private static var didAttempt = false

    /// True when the real (gitignored) plist was copied into the app bundle.
    /// Prefer `url(forResource:)` so nested / localized copies still count.
    static var hasGoogleServiceInfoPlist: Bool {
        if Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil {
            return true
        }
        // Legacy path check (same file in most installs).
        return Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil
    }

    /// Call once at launch. No-op (and no crash) when the plist is missing so stub SMS still works.
    static func configureIfNeeded() {
        guard !didAttempt else { return }
        didAttempt = true
        #if DEBUG
        switch AuthConfig.smsProviderResolution {
        case .explicitEnvOrPlist(let kind):
            print("DiaryVault SMS provider=\(kind.rawValue) (explicit DIARY_SMS_PROVIDER)")
        case .autoFirebasePlistPresent:
            print("DiaryVault SMS provider=firebase (auto: GoogleService-Info.plist in bundle)")
        case .autoStubNoPlist:
            print("DiaryVault SMS provider=stub (auto: GoogleService-Info.plist missing from bundle)")
        }
        #endif
        guard hasGoogleServiceInfoPlist else { return }
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
    }
}
