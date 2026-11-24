import Foundation

struct MirrorContext {
    var userDisplayName: String
    var recentEntries: [DiaryEntry]
    var conversation: [MirrorMessage]
    var localeIdentifier: String
}

protocol MirrorAIService {
    /// Mirror-style response: reflect + guiding questions; never coach prescriptions.
    func reply(to userText: String, context: MirrorContext) async throws -> MirrorMessage
}

enum MirrorAIError: LocalizedError {
    case notConfigured
    case emptyInput
    case network(String)
    /// Provider capacity / rate-limit / resource-exhausted (xAI etc.). Never carries raw body.
    case providerBusy
    /// HTTP 401/403 or body markers for invalid / expired API key — not the generic soft error.
    case invalidApiKey
    case decoding
    case rateLimited(limit: Int, tier: BillingConfig.Tier)

    var errorDescription: String? {
        switch self {
        case .notConfigured:
            return "Cloud AI is not configured. Set DIARY_AI_API_KEY (and optionally DIARY_AI_BASE_URL)."
        case .emptyInput:
            return "Empty message."
        case .network:
            // Opaque fallback — UI maps to localized `mirror.error.generic`.
            return "Mirror could not reach the cloud AI."
        case .providerBusy:
            return "The Mirror is busy right now. Please try again in a moment."
        case .invalidApiKey:
            return "xAI API key rejected. Check DIARY_AI_API_KEY / XAI_API_KEY (scheme or Secrets.xcconfig)."
        case .decoding:
            return "Unexpected response from AI service."
        case .rateLimited(let limit, let tier):
            switch tier {
            case .authorPro:
                return "Today’s Mirror use is finished (\(limit)). Try again tomorrow."
            case .authorPlus:
                return "Today’s Mirror use is finished (\(limit)). Author Pro raises the daily limit — or try again tomorrow."
            case .free:
                return "Free Mirror use is finished (\(limit) lifetime). Upgrade to Author Plus to continue."
            }
        }
    }

    /// Maps a non-2xx Cloud AI response to a typed error without exposing raw JSON to the UI.
    static func fromHTTP(statusCode: Int, body: Data?) -> MirrorAIError {
        let text = body.flatMap { String(data: $0, encoding: .utf8) } ?? ""
        if isProviderBusy(statusCode: statusCode, body: text) {
            return .providerBusy
        }
        if isInvalidApiKey(statusCode: statusCode, body: text) {
            return .invalidApiKey
        }
        return .network("HTTP \(statusCode)")
    }

    static func isProviderBusy(statusCode: Int, body: String) -> Bool {
        if statusCode == 429 || statusCode == 503 {
            return true
        }
        let lowered = body.lowercased()
        let markers = [
            "resource-exhausted",
            "resource_exhausted",
            "resourceexhausted",
            "rate_limit",
            "rate-limit",
            "ratelimit",
            "too many requests",
            "capacity",
            "overloaded",
            "quota exceeded",
        ]
        return markers.contains { lowered.contains($0) }
    }

    static func isInvalidApiKey(statusCode: Int, body: String) -> Bool {
        if statusCode == 401 || statusCode == 403 {
            return true
        }
        let lowered = body.lowercased()
        let markers = [
            "invalid api key",
            "incorrect api key",
            "invalid_api_key",
            "api key not found",
            "unauthorized",
            "authentication",
            "permission denied",
        ]
        return markers.contains { lowered.contains($0) }
    }
}

enum MirrorAIClientFactory {
    /// Cloud client when `DIARY_AI_API_KEY` / `XAI_API_KEY` is set; otherwise a local mock
    /// kept for unit/demo hooks. ``MirrorChatView`` refuses to send without a key (Android parity)
    /// so TestFlight never shows silent template reflections.
    @MainActor
    static func make() -> any MirrorAIService {
        guard AIConfig.useCloudClient else {
            return MockMirrorAIClient()
        }
        switch AIConfig.provider {
        case .anthropic:
            return AnthropicMirrorAIClient()
        case .openai:
            return CloudMirrorAIClient()
        }
    }
}
