import Foundation
import Combine

/// App-wide preferences and onboarding state (local only for MVP).
@MainActor
final class AppState: ObservableObject {
    private let defaults = UserDefaults.standard
    private enum Keys {
        static let didCompleteOnboarding = "didCompleteOnboarding"
        static let gentleRemindersEnabled = "gentleRemindersEnabled"
        static let displayName = "displayName"
    }

    @Published var didCompleteOnboarding: Bool {
        didSet { defaults.set(didCompleteOnboarding, forKey: Keys.didCompleteOnboarding) }
    }

    @Published var gentleRemindersEnabled: Bool {
        didSet { defaults.set(gentleRemindersEnabled, forKey: Keys.gentleRemindersEnabled) }
    }

    @Published var displayName: String {
        didSet { defaults.set(displayName, forKey: Keys.displayName) }
    }

    init() {
        didCompleteOnboarding = defaults.bool(forKey: Keys.didCompleteOnboarding)
        if defaults.object(forKey: Keys.gentleRemindersEnabled) == nil {
            gentleRemindersEnabled = true
        } else {
            gentleRemindersEnabled = defaults.bool(forKey: Keys.gentleRemindersEnabled)
        }
        displayName = defaults.string(forKey: Keys.displayName) ?? ""
    }

    func completeOnboarding(name: String) {
        displayName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        didCompleteOnboarding = true
    }

    func resetOnboardingForDemo() {
        didCompleteOnboarding = false
    }

    /// Clears display name after account deletion (onboarding flag stays so gate is reachable).
    func clearIdentityAfterAccountDeletion() {
        displayName = ""
    }
}
