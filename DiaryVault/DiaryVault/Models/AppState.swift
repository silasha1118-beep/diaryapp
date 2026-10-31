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
        /// UIDs that skipped the post-login display-name step (optional name).
        static let skippedDisplayNameUIDs = "account.skippedDisplayNameUIDs"
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

    /// UIDs that skipped post-login name onboarding (optional). Published so RootView re-gates.
    @Published private(set) var skippedDisplayNameUIDs: [String] {
        didSet { defaults.set(skippedDisplayNameUIDs, forKey: Keys.skippedDisplayNameUIDs) }
    }

    init() {
        didCompleteOnboarding = defaults.bool(forKey: Keys.didCompleteOnboarding)
        if defaults.object(forKey: Keys.gentleRemindersEnabled) == nil {
            gentleRemindersEnabled = true
        } else {
            gentleRemindersEnabled = defaults.bool(forKey: Keys.gentleRemindersEnabled)
        }
        displayName = defaults.string(forKey: Keys.displayName) ?? ""
        skippedDisplayNameUIDs = defaults.stringArray(forKey: Keys.skippedDisplayNameUIDs) ?? []
    }

    func completeOnboarding(name: String) {
        displayName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        didCompleteOnboarding = true
    }

    func resetOnboardingForDemo() {
        didCompleteOnboarding = false
    }

    /// Clears display name after account deletion (onboarding flag stays so gate is reachable).
    /// Also clears skip marks so a brand-new phone / Apple account sees the name step again.
    func clearIdentityAfterAccountDeletion() {
        displayName = ""
        skippedDisplayNameUIDs = []
    }

    func didSkipDisplayNamePrompt(for uid: String) -> Bool {
        guard !uid.isEmpty else { return false }
        return skippedDisplayNameUIDs.contains(uid)
    }

    func markDisplayNamePromptSkipped(for uid: String) {
        guard !uid.isEmpty else { return }
        guard !skippedDisplayNameUIDs.contains(uid) else { return }
        skippedDisplayNameUIDs.append(uid)
    }

    func clearDisplayNamePromptSkip(for uid: String) {
        guard !uid.isEmpty else { return }
        skippedDisplayNameUIDs.removeAll { $0 == uid }
    }
}
