import Foundation

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
