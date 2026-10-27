import Foundation

struct MirrorContext: Sendable {
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

    var errorDescription: String? {
        switch self {
        case .notConfigured:
            return "Cloud AI is not configured. Set DIARY_AI_BASE_URL and DIARY_AI_API_KEY."
        case .emptyInput:
            return "Empty message."
        case .network(let detail):
            return detail
        case .decoding:
            return "Unexpected response from AI service."
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
