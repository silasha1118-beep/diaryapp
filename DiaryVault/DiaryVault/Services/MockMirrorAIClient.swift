import Foundation

/// Local placeholder that demonstrates mirror tone without calling a network API.
struct MockMirrorAIClient: MirrorAIService {
    func reply(to userText: String, context: MirrorContext) async throws -> MirrorMessage {
        let trimmed = userText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw MirrorAIError.emptyInput }

        // Simulate latency so UI can show a thinking state.
        try await Task.sleep(nanoseconds: 450_000_000)

        let cited = context.recentEntries.prefix(2).map(\.id)
        let snippet = context.recentEntries.first.map { entry in
            let preview = entry.preview
            return preview.isEmpty ? nil : preview
        } ?? nil

        let reflection: String
        if let snippet, !snippet.isEmpty {
            reflection = String(
                format: L10n.raw("mirror.mock.withCite", locale: context.localeIdentifier),
                snippet
            )
        } else {
            reflection = L10n.raw("mirror.mock.noCite", locale: context.localeIdentifier)
        }

        let question = L10n.raw("mirror.mock.question", locale: context.localeIdentifier)
        let disclaimer = L10n.raw("mirror.mock.disclaimer", locale: context.localeIdentifier)

        let text = [reflection, "", question, "", "— \(disclaimer)"].joined(separator: "\n")

        return MirrorMessage(
            role: .mirror,
            text: text,
            citedEntryIDs: Array(cited)
        )
    }
}

/// Tiny helper so mock replies stay localized without UI coupling.
enum L10n {
    static func raw(_ key: String, locale: String) -> String {
        let table: [String: [String: String]] = [
            "mirror.mock.withCite": [
                "en": "I hear you. Looking at what you already wrote — “%@” — it seems something there still matters to you.",
                "zh-Hant": "我聽見了。對照你寫過的內容——「%@」——那裡似乎仍有對你重要的東西。",
                "zh-Hans": "我听见了。对照你写过的内容——「%@」——那里似乎仍有对你重要的东西。"
            ],
            "mirror.mock.noCite": [
                "en": "I hear you. There isn’t enough in your vault yet for me to reflect specific words back — that’s okay.",
                "zh-Hant": "我聽見了。金庫裡可引用的文字還不多——沒關係，我們可以慢慢寫。",
                "zh-Hans": "我听见了。金库里可引用的文字还不多——没关系，我们可以慢慢写。"
            ],
            "mirror.mock.question": [
                "en": "What part of that feels closest to how you are right now?",
                "zh-Hant": "這段話裡，哪一部分最接近你此刻的感覺？",
                "zh-Hans": "这段话里，哪一部分最接近你此刻的感觉？"
            ],
            "mirror.mock.disclaimer": [
                "en": "Based on what you’ve written · mock mirror (not a coach)",
                "zh-Hant": "根據你寫過的內容 · 模擬鏡子（非教練處方）",
                "zh-Hans": "根据你写过的内容 · 模拟镜子（非教练处方）"
            ]
        ]

        let lang: String
        if locale.hasPrefix("zh-Hant") || locale.hasPrefix("zh-TW") || locale.hasPrefix("zh-HK") {
            lang = "zh-Hant"
        } else if locale.hasPrefix("zh-Hans") || locale.hasPrefix("zh-CN") {
            lang = "zh-Hans"
        } else if locale.hasPrefix("zh") {
            lang = "zh-Hant"
        } else {
            lang = "en"
        }

        return table[key]?[lang] ?? table[key]?["en"] ?? key
    }
}
