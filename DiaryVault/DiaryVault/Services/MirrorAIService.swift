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
    case decoding
    case rateLimited(limit: Int, isAuthorPlus: Bool)

    var errorDescription: String? {
        switch self {
        case .notConfigured:
            return "Cloud AI is not configured. Set DIARY_AI_API_KEY (and optionally DIARY_AI_BASE_URL)."
        case .emptyInput:
            return "Empty message."
        case .network(let detail):
            return detail
        case .decoding:
            return "Unexpected response from AI service."
        case .rateLimited(let limit, let isAuthorPlus):
            if isAuthorPlus {
                return "Daily Author Plus Mirror limit reached (\(limit)). Try again tomorrow."
            }
            return "Daily free Mirror limit reached (\(limit)). Upgrade to Author Plus for a higher quota, or try again tomorrow."
        }
    }
}

enum MirrorAIClientFactory {
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
