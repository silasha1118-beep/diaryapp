import Foundation

/// Supported cloud LLM providers for Mirror.
enum AIProvider: String {
    case openai
    case anthropic
}

/// Cloud LLM configuration. No secrets belong in the repo.
///
/// Wire a cloud chat API via scheme env / Info.plist:
/// - `DIARY_AI_API_KEY` — required to leave mock mode (never commit)
/// - `DIARY_AI_PROVIDER` — optional; `openai` (default) or `anthropic`.
///   Auto-detects `anthropic` when `DIARY_AI_BASE_URL` contains `anthropic`.
/// - `DIARY_AI_BASE_URL` — optional; defaults to OpenAI or Anthropic host by provider
/// - `DIARY_AI_MODEL` — optional; defaults to `gpt-4o-mini` (openai) or `claude-sonnet-4-5` (anthropic)
/// - `DIARY_AI_MAX_TOKENS` — optional; max completion tokens (default 400)
/// - `DIARY_AI_MAX_CONTEXT_ENTRIES` — optional; diary excerpts in prompt (default 8)
/// - `DIARY_AI_FREE_DAILY_REQUEST_LIMIT` — Free-tier daily Mirror cap (default 8)
/// - `DIARY_AI_DAILY_REQUEST_LIMIT` — Author Plus daily cap (default 40; `0` = unlimited for Plus)
///
/// Data residency narrative for this product: Taiwan (TW).
enum AIConfig {
    static let defaultOpenAIBaseURLString = "https://api.openai.com"
    static let defaultAnthropicBaseURLString = "https://api.anthropic.com"
    static let defaultOpenAIModel = "gpt-4o-mini"
    static let defaultAnthropicModel = "claude-sonnet-4-5"
    /// Anthropic Messages API version header value.
    static let anthropicAPIVersion = "2023-06-01"
    static let defaultMaxTokens = 400
    static let defaultMaxContextEntries = 8
    /// Free tier: lower daily Mirror AI requests (cloud only).
    static let defaultFreeDailyRequestLimit = 8
    /// Author Plus: higher daily Mirror AI requests; `0` disables the Plus cap.
    static let defaultDailyRequestLimit = 40
    /// Soft cap on characters per diary body in the prompt (RAG-lite).
    static let maxEntryBodyCharacters = 600
    /// Soft cap on prior chat turns sent to the model.
    static let maxConversationTurns = 8

    /// Resolved provider: explicit `DIARY_AI_PROVIDER`, else auto-detect from BASE_URL, else openai.
    static var provider: AIProvider {
        if let raw = value(for: "DIARY_AI_PROVIDER")?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
            if raw == AIProvider.anthropic.rawValue { return .anthropic }
            if raw == AIProvider.openai.rawValue { return .openai }
        }
        if let base = value(for: "DIARY_AI_BASE_URL")?.lowercased(), base.contains("anthropic") {
            return .anthropic
        }
        return .openai
    }

    static var baseURL: URL? {
        if let raw = value(for: "DIARY_AI_BASE_URL"), let url = URL(string: raw) {
            return url
        }
        // Key alone is enough; default host depends on provider.
        guard apiKey != nil else { return nil }
        let fallback = provider == .anthropic
            ? defaultAnthropicBaseURLString
            : defaultOpenAIBaseURLString
        return URL(string: fallback)
    }

    static var apiKey: String? {
        value(for: "DIARY_AI_API_KEY")
    }

    static var model: String {
        if let explicit = value(for: "DIARY_AI_MODEL") {
            return explicit
        }
        return provider == .anthropic ? defaultAnthropicModel : defaultOpenAIModel
    }

    static var maxTokens: Int {
        intValue(for: "DIARY_AI_MAX_TOKENS", default: defaultMaxTokens)
    }

    static var maxContextEntries: Int {
        intValue(for: "DIARY_AI_MAX_CONTEXT_ENTRIES", default: defaultMaxContextEntries)
    }

    /// Free-tier local daily Mirror cap. Always at least 1 when cloud is used without Plus.
    static var freeDailyRequestLimit: Int {
        max(1, intValue(for: "DIARY_AI_FREE_DAILY_REQUEST_LIMIT", default: defaultFreeDailyRequestLimit))
    }

    /// Author Plus local daily Mirror cap. `0` disables the Plus-tier cap (unlimited within this stub).
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
