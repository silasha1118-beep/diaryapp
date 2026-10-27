import Foundation

struct DiaryEntry: Identifiable, Codable, Equatable, Hashable {
    var id: UUID
    var title: String
    var body: String
    var moodTag: String?
    var createdAt: Date
    var updatedAt: Date

    init(
        id: UUID = UUID(),
        title: String,
        body: String,
        moodTag: String? = nil,
        createdAt: Date = Date(),
        updatedAt: Date = Date()
    ) {
        self.id = id
        self.title = title
        self.body = body
        self.moodTag = moodTag
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    var preview: String {
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count <= 120 { return trimmed }
        return String(trimmed.prefix(120)) + "…"
    }
}
