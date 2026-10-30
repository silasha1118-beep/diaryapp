import Foundation

/// Builds the mirror-style system prompt and OpenAI-compatible message list (RAG-lite).
enum MirrorPromptBuilder {
    static func systemPrompt(context: MirrorContext, entries: [DiaryEntry]) -> String {
        let name = context.userDisplayName.trimmingCharacters(in: .whitespacesAndNewlines)
        let nameLine = name.isEmpty
            ? "The author has not set a display name."
            : "The author's preferred name is \(name)."

        let locale = context.localeIdentifier
        let languageInstruction: String
        if locale.hasPrefix("zh-Hant") || locale.hasPrefix("zh-TW") || locale.hasPrefix("zh-HK") {
            languageInstruction = "Reply in Traditional Chinese (繁體中文)."
        } else if locale.hasPrefix("zh-Hans") || locale.hasPrefix("zh-CN") {
            languageInstruction = "Reply in Simplified Chinese (简体中文)."
        } else if locale.hasPrefix("zh") {
            languageInstruction = "Reply in Traditional Chinese (繁體中文)."
        } else if locale.hasPrefix("ja") {
            languageInstruction = "Reply in Japanese (日本語)."
        } else if locale.hasPrefix("th") {
            languageInstruction = "Reply in Thai (ภาษาไทย)."
        } else {
            languageInstruction = "Reply in English."
        }

        let excerpts: String
        if entries.isEmpty {
            excerpts = "(No diary excerpts yet. If there is nothing to ground on, say so gently and invite writing — do not invent memories.)"
        } else {
            excerpts = entries.map { entry in
                let body = truncate(entry.body, limit: AIConfig.maxEntryBodyCharacters)
                let title = entry.title.isEmpty ? "(untitled)" : entry.title
                return """
                [id: \(entry.id.uuidString)]
                title: \(title)
                body: \(body)
                """
            }.joined(separator: "\n\n")
        }

        return """
        You are Mirror for Diary Vault — a gentle, grounded companion for a living author.
        Product data-residency narrative: \(AIConfig.dataResidencyLabel). Treat diary text as private; do not suggest exporting it elsewhere.

        \(nameLine)
        \(languageInstruction)

        Style (strict):
        - Mirror style only: briefly reflect / restate what the author wrote or just said, then ask 1–2 guiding questions.
        - Never give coach-style prescriptions, action plans, “you should…”, or numbered “do this next” steps.
        - Stay warm, contextual, and restrained. Prefer short paragraphs.
        - Ground claims in the diary excerpts below. When you lean on an excerpt, quote a short phrase and include its [id: …] in the reply.
        - If excerpts are thin or irrelevant, say you don’t have enough from their vault yet — do not invent facts.
        - End with a short disclaimer line: “根據你寫過的內容” / “根据你写过的内容” / “Based on what you’ve written” matching the reply language.

        Diary excerpts (most recent first; RAG-lite — not fine-tuning):
        \(excerpts)
        """
    }

    /// OpenAI chat message dictionaries (`role` + `content`), including system.
    static func chatMessages(userText: String, context: MirrorContext) -> [[String: String]] {
        let entries = Array(context.recentEntries.prefix(AIConfig.maxContextEntries))
        var messages: [[String: String]] = [
            ["role": "system", "content": systemPrompt(context: context, entries: entries)]
        ]
        messages.append(contentsOf: conversationTurns(userText: userText, context: context))
        messages.append(["role": "user", "content": userText])
        return messages
    }

    /// Anthropic Messages API shape: top-level `system` + user/assistant turns only.
    static func anthropicPrompt(userText: String, context: MirrorContext) -> (system: String, messages: [[String: String]]) {
        let entries = Array(context.recentEntries.prefix(AIConfig.maxContextEntries))
        var messages = conversationTurns(userText: userText, context: context)
        messages.append(["role": "user", "content": userText])
        return (systemPrompt(context: context, entries: entries), messages)
    }

    /// Prior user/assistant turns (no system); skips the trailing duplicate of `userText`.
    private static func conversationTurns(userText: String, context: MirrorContext) -> [[String: String]] {
        let prior = context.conversation
            .filter { $0.role == .user || $0.role == .mirror }
            .suffix(AIConfig.maxConversationTurns)

        var messages: [[String: String]] = []
        for message in prior {
            // Skip the just-appended user turn if the UI already added it; we send `userText` last.
            if message.role == .user && message.text == userText { continue }
            let role = message.role == .user ? "user" : "assistant"
            messages.append(["role": role, "content": message.text])
        }
        return messages
    }

    static func citedIDs(in text: String, from entries: [DiaryEntry]) -> [UUID] {
        let known = Set(entries.map(\.id))
        var found: [UUID] = []
        var seen = Set<UUID>()
        // Match bare UUIDs or [id: UUID] forms present in the model reply.
        let pattern = #"[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        regex.enumerateMatches(in: text, range: range) { match, _, _ in
            guard let match, let swiftRange = Range(match.range, in: text) else { return }
            if let id = UUID(uuidString: String(text[swiftRange])), known.contains(id), !seen.contains(id) {
                seen.insert(id)
                found.append(id)
            }
        }
        return found
    }

    private static func truncate(_ text: String, limit: Int) -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count > limit else { return trimmed }
        return String(trimmed.prefix(limit)) + "…"
    }
}
