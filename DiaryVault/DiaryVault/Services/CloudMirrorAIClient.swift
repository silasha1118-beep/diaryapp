import Foundation

/// Stub cloud client. Activated only when `AIConfig.useCloudClient` is true.
/// Does not embed secrets; reads `DIARY_AI_*` from environment / Info.plist.
struct CloudMirrorAIClient: MirrorAIService {
    func reply(to userText: String, context: MirrorContext) async throws -> MirrorMessage {
        let trimmed = userText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw MirrorAIError.emptyInput }

        guard let baseURL = AIConfig.baseURL, let apiKey = AIConfig.apiKey else {
            throw MirrorAIError.notConfigured
        }

        // Expected contract (illustrative): POST {baseURL}/v1/mirror
        // Body includes user message, recent entry summaries, locale, and mirror style constraints.
        // Response: { "text": "...", "citedEntryIds": ["..."] }
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/mirror"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.timeoutInterval = 60

        let payload: [String: Any] = [
            "model": AIConfig.model,
            "locale": context.localeIdentifier,
            "dataResidency": AIConfig.dataResidencyLabel,
            "style": "mirror", // reflect + guiding questions; no coach prescriptions
            "message": trimmed,
            "displayName": context.userDisplayName,
            "entries": context.recentEntries.prefix(12).map { entry in
                [
                    "id": entry.id.uuidString,
                    "title": entry.title,
                    "body": entry.body,
                    "createdAt": ISO8601DateFormatter().string(from: entry.createdAt)
                ]
            }
        ]

        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw MirrorAIError.network("Cloud AI HTTP \(code)")
        }

        guard
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let text = json["text"] as? String
        else {
            throw MirrorAIError.decoding
        }

        let cited: [UUID]
        if let ids = json["citedEntryIds"] as? [String] {
            cited = ids.compactMap(UUID.init(uuidString:))
        } else {
            cited = []
        }

        return MirrorMessage(role: .mirror, text: text, citedEntryIDs: cited)
    }
}
