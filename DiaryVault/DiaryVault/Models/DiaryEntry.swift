import Foundation

// MARK: - Media attachments (kept in this file so the type is always in the DiaryVault target)

/// Kind of media attached to a diary entry.
enum DiaryMediaKind: String, Codable, Equatable, Hashable, CaseIterable {
    case voice
    case image
    case video

    var systemImage: String {
        switch self {
        case .voice: return "waveform"
        case .image: return "photo"
        case .video: return "video"
        }
    }

    var defaultFileExtension: String {
        switch self {
        case .voice: return "m4a"
        case .image: return "jpg"
        case .video: return "mp4"
        }
    }

    var defaultMIMEType: String {
        switch self {
        case .voice: return "audio/mp4"
        case .image: return "image/jpeg"
        case .video: return "video/mp4"
        }
    }

    /// Localization key for vault list / composer labels.
    var localizationKey: String {
        switch self {
        case .voice: return "diary.media.voice"
        case .image: return "diary.media.photo"
        case .video: return "diary.media.video"
        }
    }
}

/// Local + cloud reference for one media file on a diary entry.
/// Local files live under Documents/diary_media/{entryId}/{localFileName}.
/// Cloud objects live at users/{uid}/entries/{entryId}/{localFileName} (Firebase Storage).
struct DiaryMediaAttachment: Identifiable, Codable, Equatable, Hashable {
    var id: UUID
    var kind: DiaryMediaKind
    /// Filename only (no path separators) under the entry media folder.
    var localFileName: String
    /// Firebase Storage object path under the bucket (nil until uploaded).
    var remotePath: String?
    var mimeType: String
    var durationSeconds: Double?
    var byteSize: Int?
    var createdAt: Date

    init(
        id: UUID = UUID(),
        kind: DiaryMediaKind,
        localFileName: String,
        remotePath: String? = nil,
        mimeType: String? = nil,
        durationSeconds: Double? = nil,
        byteSize: Int? = nil,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.kind = kind
        self.localFileName = localFileName
        self.remotePath = remotePath
        self.mimeType = mimeType ?? kind.defaultMIMEType
        self.durationSeconds = durationSeconds
        self.byteSize = byteSize
        self.createdAt = createdAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id)
        kind = try c.decode(DiaryMediaKind.self, forKey: .kind)
        localFileName = try c.decode(String.self, forKey: .localFileName)
        remotePath = try c.decodeIfPresent(String.self, forKey: .remotePath)
        mimeType = try c.decodeIfPresent(String.self, forKey: .mimeType) ?? kind.defaultMIMEType
        durationSeconds = try c.decodeIfPresent(Double.self, forKey: .durationSeconds)
        byteSize = try c.decodeIfPresent(Int.self, forKey: .byteSize)
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date()
    }
}

// MARK: - Diary entry

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
    /// Voice / image / video attachments (local filenames + optional Storage paths).
    var attachments: [DiaryMediaAttachment]

    init(
        id: UUID = UUID(),
        title: String,
        body: String,
        moodTag: String? = nil,
        createdAt: Date = Date(),
        updatedAt: Date = Date(),
        deleted: Bool = false,
        deletedAt: Date? = nil,
        attachments: [DiaryMediaAttachment] = []
    ) {
        self.id = id
        self.title = title
        self.body = body
        self.moodTag = moodTag
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deleted = deleted
        self.deletedAt = deletedAt
        self.attachments = attachments
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
        // Pre-media local JSON omits attachments.
        attachments = try c.decodeIfPresent([DiaryMediaAttachment].self, forKey: .attachments) ?? []
    }

    var hasMedia: Bool { !attachments.isEmpty }

    var preview: String {
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return "" }
        if trimmed.count <= 120 { return trimmed }
        return String(trimmed.prefix(120)) + "…"
    }

    /// Stable kinds present (for list row icons) — order: voice, image, video.
    var mediaKindsPresent: [DiaryMediaKind] {
        DiaryMediaKind.allCases.filter { kind in
            attachments.contains { $0.kind == kind }
        }
    }
}
