import Foundation

/// OpenAI-compatible chat completions client (`POST /v1/chat/completions`).
/// Activated when `AIConfig.useCloudClient` is true and provider is openai
/// (Groq, DeepSeek, Azure OpenAI chat path, etc.).
/// Does not embed secrets; reads `DIARY_AI_*` from environment / Info.plist.
struct CloudMirrorAIClient: MirrorAIService {
    func reply(to userText: String, context: MirrorContext) async throws -> MirrorMessage {
        let trimmed = userText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw MirrorAIError.emptyInput }

        guard let baseURL = AIConfig.baseURL, let apiKey = AIConfig.apiKey else {
            throw MirrorAIError.notConfigured
        }

        try MirrorUsageGuard.checkAndIncrement()

        let endpoint = baseURL
            .appendingPathComponent("v1")
            .appendingPathComponent("chat")
            .appendingPathComponent("completions")

        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.timeoutInterval = 60

        let messages = MirrorPromptBuilder.chatMessages(userText: trimmed, context: context)
        let payload: [String: Any] = [
            "model": AIConfig.model,
            "max_tokens": AIConfig.maxTokens,
            "temperature": 0.7,
            "messages": messages
        ]

        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw MirrorAIError.network("Cloud AI: no HTTP response")
        }
        guard (200..<300).contains(http.statusCode) else {
            let detail = String(data: data, encoding: .utf8)?
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let snippet = (detail?.isEmpty == false)
                ? String(detail!.prefix(180))
                : "HTTP \(http.statusCode)"
            throw MirrorAIError.network("Cloud AI \(snippet)")
        }

        guard
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let choices = json["choices"] as? [[String: Any]],
            let first = choices.first,
            let message = first["message"] as? [String: Any],
            let text = message["content"] as? String
        else {
            throw MirrorAIError.decoding
        }

        let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { throw MirrorAIError.decoding }

        let contextEntries = Array(context.recentEntries.prefix(AIConfig.maxContextEntries))
        let cited = MirrorPromptBuilder.citedIDs(in: cleaned, from: contextEntries)

        return MirrorMessage(role: .mirror, text: cleaned, citedEntryIDs: cited)
    }
}
