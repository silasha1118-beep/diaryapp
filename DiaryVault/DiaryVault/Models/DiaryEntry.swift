import Foundation

struct DiaryEntry: Identifiable, Codable, Equatable, Hashable {
    var id: UUID
    var title: String
    var body: String
    var moodTag: String?
    var createdAt: Date
    var updatedAt: Date
    /// Soft-delete flag — entry stays in local JSON and Firestore forever when true.
    var deleted: Bool
    /// When the user deleted this entry (nil if never soft-deleted).
    var deletedAt: Date?

    init(
        id: UUID = UUID(),
        title: String,
        body: String,
        moodTag: String? = nil,
        createdAt: Date = Date(),
        updatedAt: Date = Date(),
        deleted: Bool = false,
        deletedAt: Date? = nil
    ) {
        self.id = id
        self.title = title
        self.body = body
        self.moodTag = moodTag
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deleted = deleted
        self.deletedAt = deletedAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id)
        title = try c.decode(String.self, forKey: .title)
        body = try c.decode(String.self, forKey: .body)
        moodTag = try c.decodeIfPresent(String.self, forKey: .moodTag)
        createdAt = try c.decode(Date.self, forKey: .createdAt)
        updatedAt = try c.decode(Date.self, forKey: .updatedAt)
        // Pre-soft-delete local JSON omits these keys.
        deleted = try c.decodeIfPresent(Bool.self, forKey: .deleted) ?? false
        deletedAt = try c.decodeIfPresent(Date.self, forKey: .deletedAt)
    }

    var preview: String {
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count <= 120 { return trimmed }
        return String(trimmed.prefix(120)) + "…"
    }
}
