import Foundation

enum MirrorRole: String, Codable {
    case user
    case mirror
    case system
}

struct MirrorMessage: Identifiable, Codable, Equatable {
    var id: UUID
    var role: MirrorRole
    var text: String
    var citedEntryIDs: [UUID]
    var createdAt: Date

    init(
        id: UUID = UUID(),
        role: MirrorRole,
        text: String,
        citedEntryIDs: [UUID] = [],
        createdAt: Date = Date()
    ) {
        self.id = id
        self.role = role
        self.text = text
        self.citedEntryIDs = citedEntryIDs
        self.createdAt = createdAt
    }
}
