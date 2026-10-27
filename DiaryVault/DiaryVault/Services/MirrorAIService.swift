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
    case rateLimited(Int)

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
        case .rateLimited(let limit):
            return "Daily Mirror request limit reached (\(limit)). Try again tomorrow, raise DIARY_AI_DAILY_REQUEST_LIMIT, or unset the key to use mock."
        }
    }
}

enum MirrorAIClientFactory {
    @MainActor
    static func make() -> any MirrorAIService {
        if AIConfig.useCloudClient {
            return CloudMirrorAIClient()
        }
        return MockMirrorAIClient()
    }
}
