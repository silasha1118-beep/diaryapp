import Foundation

/// Anthropic Messages API client (`POST /v1/messages`).
/// Activated when `AIConfig.useCloudClient` is true and provider resolves to anthropic.
/// Headers: `x-api-key`, `anthropic-version`. Does not embed secrets.
struct AnthropicMirrorAIClient: MirrorAIService {
    func reply(to userText: String, context: MirrorContext) async throws -> MirrorMessage {
        let trimmed = userText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw MirrorAIError.emptyInput }

        guard let baseURL = AIConfig.baseURL, let apiKey = AIConfig.apiKey else {
            throw MirrorAIError.notConfigured
        }

        try MirrorUsageGuard.checkAndIncrement()

        let endpoint = baseURL
            .appendingPathComponent("v1")
            .appendingPathComponent("messages")

        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(apiKey, forHTTPHeaderField: "x-api-key")
        request.setValue(AIConfig.anthropicAPIVersion, forHTTPHeaderField: "anthropic-version")
        request.timeoutInterval = 60

        let prompt = MirrorPromptBuilder.anthropicPrompt(userText: trimmed, context: context)
        let payload: [String: Any] = [
            "model": AIConfig.model,
            "max_tokens": AIConfig.maxTokens,
            "temperature": 0.7,
            "system": prompt.system,
            "messages": prompt.messages
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
            let content = json["content"] as? [[String: Any]]
        else {
            throw MirrorAIError.decoding
        }

        let textParts = content.compactMap { block -> String? in
            guard (block["type"] as? String) == "text" else { return nil }
            return block["text"] as? String
        }
        let cleaned = textParts.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { throw MirrorAIError.decoding }

        let contextEntries = Array(context.recentEntries.prefix(AIConfig.maxContextEntries))
        let cited = MirrorPromptBuilder.citedIDs(in: cleaned, from: contextEntries)

        return MirrorMessage(role: .mirror, text: cleaned, citedEntryIDs: cited)
    }
}
