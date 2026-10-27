import Foundation

/// Cloud LLM configuration stub. No secrets belong in the repo.
///
/// Wire a real provider by setting Info.plist keys or scheme environment variables:
/// - `DIARY_AI_BASE_URL` — API base URL (e.g. https://api.example.com)
/// - `DIARY_AI_API_KEY` — secret key (use Xcode scheme env / CI secrets, never commit)
/// - `DIARY_AI_MODEL` — optional model id
///
/// Data residency narrative for this product: Taiwan (TW). MVP scaffold does not
/// ship a production endpoint; `CloudMirrorAIClient` remains a stub until keys exist.
enum AIConfig {
    static var baseURL: URL? {
        value(for: "DIARY_AI_BASE_URL").flatMap(URL.init(string:))
    }

    static var apiKey: String? {
        value(for: "DIARY_AI_API_KEY")
    }

    static var model: String {
        value(for: "DIARY_AI_MODEL") ?? "mirror-v0"
    }

    /// Prefer mock unless both base URL and API key are present.
    static var useCloudClient: Bool {
        baseURL != nil && !(apiKey?.isEmpty ?? true)
    }

    static var dataResidencyLabel: String { "Taiwan (TW)" }

    private static func value(for key: String) -> String? {
        if let env = ProcessInfo.processInfo.environment[key], !env.isEmpty {
            return env
        }
        if let plist = Bundle.main.object(forInfoDictionaryKey: key) as? String, !plist.isEmpty {
            return plist
        }
        return nil
    }
}
