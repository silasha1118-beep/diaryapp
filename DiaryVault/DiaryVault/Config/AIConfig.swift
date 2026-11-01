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
/// - `DIARY_AI_FREE_LIFETIME_REQUEST_LIMIT` — Free-tier Mirror lifetime hard cap (default 40; alias `DIARY_AI_FREE_DAILY_REQUEST_LIMIT`)
/// - `DIARY_AI_DAILY_REQUEST_LIMIT` — Author Plus daily hard cap (default 8; `0` = unlimited for that tier)
/// - `DIARY_AI_PRO_DAILY_REQUEST_LIMIT` — Author Pro daily hard cap (default 16; `0` = unlimited for Pro)
///
/// Soft Free Mirror ladders removed (2026-11-01). Mirror quotas stay independent of diary
/// **upload** quotas (``UploadQuotaConfig`` / ``DiaryUploadUsageGuard``).
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
    /// Free tier: lifetime Mirror AI requests (cloud only). Hard stop → paywall.
    static let defaultFreeLifetimeRequestLimit = 40
    /// Author Plus: daily Mirror AI hard cap; `0` disables the Plus cap.
    static let defaultDailyRequestLimit = 8
    /// Author Pro: daily Mirror AI hard cap; `0` disables the Pro cap.
    static let defaultProDailyRequestLimit = 16
    /// Soft cap on characters per diary body in the prompt (RAG-lite).
    static let maxEntryBodyCharacters = 600
    /// Soft cap on prior chat turns sent to the model.
    static let maxConversationTurns = 8
    // TODO(DA-15): Soft duration caps for voice (~60–90s) and video (~30–60s) diary media — not enforced yet.
    // Multimedia **upload count** caps live in UploadQuotaConfig (same vault bucket as text).

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

    /// Free-tier Mirror lifetime hard cap. Always at least 1 when cloud is used without Plus.
    /// Prefers `DIARY_AI_FREE_LIFETIME_REQUEST_LIMIT`; falls back to legacy daily key.
    static var freeLifetimeRequestLimit: Int {
        if let raw = value(for: "DIARY_AI_FREE_LIFETIME_REQUEST_LIMIT"), let parsed = Int(raw) {
            return max(1, parsed)
        }
        return max(1, intValue(for: "DIARY_AI_FREE_DAILY_REQUEST_LIMIT", default: defaultFreeLifetimeRequestLimit))
    }

    /// Legacy alias — Free is lifetime, not daily.
    static var freeDailyRequestLimit: Int { freeLifetimeRequestLimit }

    /// Soft ladders removed — always `0` so leftover call sites never nudge.
    static var freeSoftPaywallThreshold: Int { 0 }

    /// Author Plus local daily Mirror cap. `0` disables the Plus-tier cap (unlimited within this stub).
    static var dailyRequestLimit: Int {
        intValue(for: "DIARY_AI_DAILY_REQUEST_LIMIT", default: defaultDailyRequestLimit)
    }

    /// Author Pro local daily Mirror cap. `0` disables the Pro-tier cap.
    static var proDailyRequestLimit: Int {
        intValue(for: "DIARY_AI_PRO_DAILY_REQUEST_LIMIT", default: defaultProDailyRequestLimit)
    }

    /// Prefer mock unless an API key is present.
    static var useCloudClient: Bool {
        !(apiKey?.isEmpty ?? true)
    }

    /// Prompt / internal residency cue — keep ISO-ish for model context; Settings uses localized copy.
    static var dataResidencyLabel: String { "Taiwan" }

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
