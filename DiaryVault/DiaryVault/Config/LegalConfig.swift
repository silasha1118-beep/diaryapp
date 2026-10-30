import Foundation

/// Public legal URLs for App Store subscription disclosure (Guideline 3.1.2).
/// Set real hosted pages via Info.plist / scheme env before submit — leave empty to use in-app sheets.
enum LegalConfig {
    /// `DIARY_PRIVACY_POLICY_URL` — https privacy policy (required for IAP review).
    static var privacyPolicyURL: URL? {
        url(for: "DIARY_PRIVACY_POLICY_URL")
    }

    /// `DIARY_TERMS_OF_USE_URL` — Terms of Use / EULA (or Apple Standard EULA link).
    static var termsOfUseURL: URL? {
        url(for: "DIARY_TERMS_OF_USE_URL")
    }

    /// Apple’s standard EULA — acceptable Terms link when you don’t host a custom EULA.
    static let appleStandardEULA = URL(
        string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/"
    )!

    static var effectiveTermsURL: URL {
        termsOfUseURL ?? appleStandardEULA
    }

    private static func url(for key: String) -> URL? {
        let raw: String?
        if let env = ProcessInfo.processInfo.environment[key], !env.isEmpty {
            raw = env
        } else if let plist = Bundle.main.object(forInfoDictionaryKey: key) as? String, !plist.isEmpty {
            raw = plist
        } else {
            raw = nil
        }
        guard let raw, let url = URL(string: raw), url.scheme == "https" else { return nil }
        return url
    }
}
