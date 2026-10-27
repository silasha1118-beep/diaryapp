import Foundation

/// Cloud LLM configuration. No secrets belong in the repo.
///
/// Wire an OpenAI-compatible chat API via scheme env / Info.plist:
/// - `DIARY_AI_API_KEY` — required to leave mock mode (never commit)
/// - `DIARY_AI_BASE_URL` — optional; defaults to `https://api.openai.com`
/// - `DIARY_AI_MODEL` — optional; defaults to `gpt-4o-mini`
/// - `DIARY_AI_MAX_TOKENS` — optional; max completion tokens (default 400)
/// - `DIARY_AI_MAX_CONTEXT_ENTRIES` — optional; diary excerpts in prompt (default 8)
/// - `DIARY_AI_DAILY_REQUEST_LIMIT` — optional; local daily cap stub (default 40; `0` = off)
///
/// Data residency narrative for this product: Taiwan (TW).
enum AIConfig {
    static let defaultBaseURLString = "https://api.openai.com"
    static let defaultModel = "gpt-4o-mini"
    static let defaultMaxTokens = 400
    static let defaultMaxContextEntries = 8
    static let defaultDailyRequestLimit = 40
    /// Soft cap on characters per diary body in the prompt (RAG-lite).
    static let maxEntryBodyCharacters = 600
    /// Soft cap on prior chat turns sent to the model.
    static let maxConversationTurns = 8

    static var baseURL: URL? {
        if let raw = value(for: "DIARY_AI_BASE_URL"), let url = URL(string: raw) {
            return url
        }
        // Key alone is enough; default to OpenAI-compatible host.
        guard apiKey != nil else { return nil }
        return URL(string: defaultBaseURLString)
    }

    static var apiKey: String? {
        value(for: "DIARY_AI_API_KEY")
    }

    static var model: String {
        value(for: "DIARY_AI_MODEL") ?? defaultModel
    }

    static var maxTokens: Int {
        intValue(for: "DIARY_AI_MAX_TOKENS", default: defaultMaxTokens)
    }

    static var maxContextEntries: Int {
        intValue(for: "DIARY_AI_MAX_CONTEXT_ENTRIES", default: defaultMaxContextEntries)
    }

    /// Local stub counter. `0` disables the cap.
    static var dailyRequestLimit: Int {
        intValue(for: "DIARY_AI_DAILY_REQUEST_LIMIT", default: defaultDailyRequestLimit)
    }

    /// Prefer mock unless an API key is present.
    static var useCloudClient: Bool {
        !(apiKey?.isEmpty ?? true)
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

    private static func intValue(for key: String, default defaultValue: Int) -> Int {
        guard let raw = value(for: key), let parsed = Int(raw) else { return defaultValue }
        return max(0, parsed)
    }
}
