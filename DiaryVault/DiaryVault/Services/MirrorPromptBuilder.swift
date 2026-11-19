import Foundation

/// Builds the mirror-style system prompt and OpenAI-compatible message list (RAG-lite).
enum MirrorPromptBuilder {
    private static let uuidPattern =
        #"[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}"#
    private static let idMarkerPattern =
        #"\[id:\s*[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}\]"#

    /// Boilerplate attribution closings the model used to be forced to append.
    /// The chat UI already shows “依據你的日記” under grounded bubbles — strip these from the body.
    private static let attributionBoilerplatePatterns: [String] = [
        #"^[\s—–\-·•]*根據你寫過的內容.*$"#,
        #"^[\s—–\-·•]*根据你写过的内容.*$"#,
        #"^[\s—–\-·•]*Based on what you(?:'|’)?ve written.*$"#,
        #"^[\s—–\-·•]*書いた内容に基づく.*$"#,
        #"^[\s—–\-·•]*อิงจากสิ่งที่คุณเขียน.*$"#,
    ]

    private static let excerptDateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone.current
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

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
                let date = excerptDateFormatter.string(from: entry.createdAt)
                return """
                [id: \(entry.id.uuidString)]
                date: \(date)
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
        - Ground claims in the diary excerpts below. When you lean on an excerpt, cite it in natural language only — paraphrase by date, title, or a short quoted phrase (e.g. “in your note from March…” / “you wrote about…”).
        - Never print entry IDs, UUIDs, or `[id: …]` markers in the reply. Those markers are for your private grounding only.
        - If excerpts are thin or irrelevant, say you don’t have enough from their vault yet — do not invent facts.
        - Do not end with attribution boilerplate such as “根據你寫過的內容”, “根据你写过的内容”, or “Based on what you’ve written”. The app UI already shows diary grounding — keep the reply body natural chat only.

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

    /// Extract known entry IDs mentioned in model text (before sanitizing for display).
    static func citedIDs(in text: String, from entries: [DiaryEntry]) -> [UUID] {
        let known = Set(entries.map(\.id))
        var found: [UUID] = []
        var seen = Set<UUID>()
        guard let regex = try? NSRegularExpression(pattern: uuidPattern) else { return [] }
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

    /// Defensive clean-up: strip leaked `[id:…]` / UUID markers and forced attribution closings.
    static func sanitizeAssistantText(_ text: String) -> String {
        var result = text
        if let idMarker = try? NSRegularExpression(pattern: idMarkerPattern, options: .caseInsensitive) {
            let range = NSRange(result.startIndex..<result.endIndex, in: result)
            result = idMarker.stringByReplacingMatches(in: result, range: range, withTemplate: "")
        }
        if let uuid = try? NSRegularExpression(pattern: uuidPattern, options: .caseInsensitive) {
            let range = NSRange(result.startIndex..<result.endIndex, in: result)
            result = uuid.stringByReplacingMatches(in: result, range: range, withTemplate: "")
        }

        let lines = result.components(separatedBy: .newlines)
        let kept = lines.filter { line in
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty { return true }
            for pattern in attributionBoilerplatePatterns {
                if let re = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
                   re.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..<trimmed.endIndex, in: trimmed)) != nil {
                    return false
                }
            }
            return true
        }

        // Collapse leftover blank runs from stripped lines / markers.
        var collapsed: [String] = []
        var pendingBlank = false
        for line in kept {
            if line.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                pendingBlank = !collapsed.isEmpty
                continue
            }
            if pendingBlank {
                collapsed.append("")
                pendingBlank = false
            }
            // Also tidy double-spaces left where an ID was mid-sentence.
            let tidied = line
                .replacingOccurrences(of: #"\s{2,}"#, with: " ", options: .regularExpression)
                .trimmingCharacters(in: .whitespaces)
            collapsed.append(tidied)
        }

        return collapsed.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Parse citations from the raw model reply, sanitize display text, and fall back to context entries for the UI badge.
    static func finalizeAssistantReply(rawText: String, contextEntries: [DiaryEntry]) -> (text: String, citedEntryIDs: [UUID]) {
        let extracted = citedIDs(in: rawText, from: contextEntries)
        let cleaned = sanitizeAssistantText(rawText)
        let cited = extracted.isEmpty && !contextEntries.isEmpty
            ? Array(contextEntries.prefix(2).map(\.id))
            : extracted
        return (cleaned, cited)
    }

    private static func truncate(_ text: String, limit: Int) -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count > limit else { return trimmed }
        return String(trimmed.prefix(limit)) + "…"
    }
}
